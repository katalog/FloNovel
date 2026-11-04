//go:build windows

package main

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"syscall"
	"unsafe"
)

var (
	user32                                                                = syscall.NewLazyDLL("user32.dll")
	kernel32                                                              = syscall.NewLazyDLL("kernel32.dll")
	shell32                                                               = syscall.NewLazyDLL("shell32.dll")
	createWindow                                                          = user32.NewProc("CreateWindowExW")
	defWindowProc                                                         = user32.NewProc("DefWindowProcW")
	sendMessage                                                           = user32.NewProc("SendMessageW")
	postMessage                                                           = user32.NewProc("PostMessageW")
	setText                                                               = user32.NewProc("SetWindowTextW")
	enableWindow                                                          = user32.NewProc("EnableWindow")
	messageBox                                                            = user32.NewProc("MessageBoxW")
	dragQuery                                                             = shell32.NewProc("DragQueryFileW")
	dragFinish                                                            = shell32.NewProc("DragFinish")
	dragAccept                                                            = shell32.NewProc("DragAcceptFiles")
	callWindowProc                                                        = user32.NewProc("CallWindowProcW")
	getWindowText                                                         = user32.NewProc("GetWindowTextW")
	getWindowTextLength                                                   = user32.NewProc("GetWindowTextLengthW")
	window, logWindow, patternInput, startButton, clearButton, oldLogProc uintptr
	queue                                                                 []string
	busy, completed                                                       bool
	warningCount                                                          int
	logText                                                               string
	updates                                                               = make(chan update, 32)
)

const wmDropFiles = 0x0233
const wmUpdate = 0x8001

type update struct {
	text          string
	done, warning bool
}

type windowClass struct {
	Size, Style                        uint32
	Proc                               uintptr
	ClassExtra, WindowExtra            int32
	Instance, Icon, Cursor, Background uintptr
	MenuName, ClassName                *uint16
	SmallIcon                          uintptr
}

type message struct {
	Window         uintptr
	ID             uint32
	WParam, LParam uintptr
	Time           uint32
	X, Y           int32
	Private        uint32
}

func wide(text string) *uint16 { value, _ := syscall.UTF16PtrFromString(text); return value }

func showMessage(text string, warning bool) {
	flags := uintptr(0x40)
	if warning {
		flags = 0x30
	}
	messageBox.Call(window, uintptr(unsafe.Pointer(wide(text))), uintptr(unsafe.Pointer(wide("FloNovel Text Splitter"))), flags)
}

func control(class, text string, style uintptr, x, y, width, height int, id uintptr) uintptr {
	handle, _, _ := createWindow.Call(0, uintptr(unsafe.Pointer(wide(class))), uintptr(unsafe.Pointer(wide(text))),
		0x50000000|style, uintptr(x), uintptr(y), uintptr(width), uintptr(height), window, id, 0, 0)
	font, _, _ := syscall.NewLazyDLL("gdi32.dll").NewProc("GetStockObject").Call(17)
	sendMessage.Call(handle, 0x30, font, 1)
	return handle
}

func refreshLog() {
	setText.Call(logWindow, uintptr(unsafe.Pointer(wide(logText))))
	sendMessage.Call(logWindow, 0xB1, ^uintptr(0), ^uintptr(0))
	sendMessage.Call(logWindow, 0xB7, 0, 0)
}

func getPattern() (string, error) {
	if patternInput == 0 {
		return defaultChapterPattern, nil
	}
	length, _, _ := getWindowTextLength.Call(patternInput)
	if length == 0 {
		return defaultChapterPattern, nil
	}
	buf := make([]uint16, length+1)
	getWindowText.Call(patternInput, uintptr(unsafe.Pointer(&buf[0])), length+1)
	text := strings.TrimSpace(syscall.UTF16ToString(buf))
	if text == "" {
		return defaultChapterPattern, nil
	}
	if _, err := regexp.Compile(text); err != nil {
		return "", err
	}
	return text, nil
}

func acceptDrop(drop uintptr) {
	defer dragFinish.Call(drop)
	if busy {
		return
	}
	if completed {
		queue = nil
		logText = ""
		completed = false
	}
	count, _, _ := dragQuery.Call(drop, 0xffffffff, 0, 0)
	for index := uintptr(0); index < count; index++ {
		length, _, _ := dragQuery.Call(drop, index, 0, 0)
		buffer := make([]uint16, length+1)
		dragQuery.Call(drop, index, uintptr(unsafe.Pointer(&buffer[0])), length+1)
		path := filepath.Clean(syscall.UTF16ToString(buffer))
		duplicate := false
		for _, existing := range queue {
			if strings.EqualFold(existing, path) {
				duplicate = true
				break
			}
		}
		if duplicate {
			continue
		}
		queue = append(queue, path)
		logText += fmt.Sprintf("Queued: %s\r\n", path)
	}
	refreshLog()
	if len(queue) > 0 {
		enableWindow.Call(startButton, 1)
	}
}

func beginBatch() {
	if busy || len(queue) == 0 {
		return
	}
	pattern, err := getPattern()
	if err != nil {
		showMessage("Invalid chapter regex pattern:\n\n"+err.Error(), true)
		return
	}
	busy, warningCount = true, 0
	enableWindow.Call(startButton, 0)
	enableWindow.Call(clearButton, 0)
	enableWindow.Call(patternInput, 0)
	paths := append([]string(nil), queue...)
	go func() {
		notify := func(item update) { updates <- item; postMessage.Call(window, wmUpdate, 0, 0) }
		notify(update{text: fmt.Sprintf("Using chapter pattern: %s\r\n", pattern)})
		var failures []fileFailure
		for index, path := range paths {
			notify(update{text: fmt.Sprintf("\r\n[%d/%d] Processing: %s\r\n", index+1, len(paths), path)})
			result, err := split(path, pattern)
			if err != nil {
				failures = append(failures, fileFailure{Path: path, Reason: err.Error()})
				notify(update{text: "WARNING / FAILED: " + err.Error() + "\r\n", warning: true})
				continue
			}
			text := fmt.Sprintf("Done: %d parts\r\n", len(result.Files))
			for i, output := range result.Files {
				sizeStr := ""
				if info, statErr := os.Stat(output); statErr == nil {
					sizeStr = fmt.Sprintf(" (%.2f MiB)", float64(info.Size())/float64(mib))
				}
				overlapNote := ""
				if i > 0 && i < len(result.Intervals) && result.Intervals[i].Start < result.Intervals[i-1].End {
					overlapBytes := result.Intervals[i-1].End - result.Intervals[i].Start
					overlapNote = fmt.Sprintf(" [overlapped %.1f KiB with #%d]", float64(overlapBytes)/1024.0, i)
				}
				text += fmt.Sprintf("  %s%s%s\r\n", output, sizeStr, overlapNote)
			}
			text += "Original backup: " + result.Backup + "\r\n"
			notify(update{text: text})
		}
		if len(failures) > 0 {
			notify(update{text: formatFailureSummary(failures)})
		}
		notify(update{text: "\r\nBatch finished. Drop more files to begin a new queue.\r\n", done: true})
	}()
}

func windowProc(hwnd uintptr, msg uint32, wParam, lParam uintptr) uintptr {
	switch msg {
	case wmDropFiles:
		acceptDrop(wParam)
		return 0
	case 0x0111: // WM_COMMAND
		switch wParam & 0xffff {
		case 101:
			beginBatch()
		case 102:
			if !busy {
				queue = nil
				logText = ""
				completed = false
				refreshLog()
				enableWindow.Call(startButton, 0)
			}
		}
		return 0
	case wmUpdate:
		for {
			select {
			case item := <-updates:
				logText += item.text
				if item.warning {
					warningCount++
				}
				refreshLog()
				if item.done {
					busy, completed = false, true
					enableWindow.Call(clearButton, 1)
					enableWindow.Call(patternInput, 1)
					if warningCount > 0 {
						showMessage(fmt.Sprintf("%d file(s) were skipped or failed.\nReview the log for details. Other files were processed independently.", warningCount), true)
					}
				}
			default:
				return 0
			}
		}
	case 0x0010: // WM_CLOSE
		if busy {
			showMessage("Please wait for the batch to finish before closing.", true)
			return 0
		}
		user32.NewProc("DestroyWindow").Call(hwnd)
		return 0
	case 0x0002: // WM_DESTROY
		user32.NewProc("PostQuitMessage").Call(0)
		return 0
	}
	result, _, _ := defWindowProc.Call(hwnd, uintptr(msg), wParam, lParam)
	return result
}

func logProc(hwnd uintptr, msg uint32, wParam, lParam uintptr) uintptr {
	if msg == wmDropFiles {
		acceptDrop(wParam)
		return 0
	}
	result, _, _ := callWindowProc.Call(oldLogProc, hwnd, uintptr(msg), wParam, lParam)
	return result
}

func main() {
	// Win32 windows and their message pump must remain on the creating OS thread.
	runtime.LockOSThread()
	instance, _, _ := kernel32.NewProc("GetModuleHandleW").Call(0)
	cursor, _, _ := user32.NewProc("LoadCursorW").Call(0, 32512)
	class := windowClass{Proc: syscall.NewCallback(windowProc), Instance: instance, Cursor: cursor,
		Background: 16, ClassName: wide("FloNovelTextSplitterWindow")}
	class.Size = uint32(unsafe.Sizeof(class))
	registered, _, err := user32.NewProc("RegisterClassExW").Call(uintptr(unsafe.Pointer(&class)))
	if registered == 0 {
		showMessage("Cannot register window: "+err.Error(), true)
		return
	}
	window, _, err = createWindow.Call(0, uintptr(unsafe.Pointer(class.ClassName)),
		uintptr(unsafe.Pointer(wide("FloNovel Text Splitter"))), 0x00ca0000,
		0x80000000, 0x80000000, 980, 650, 0, 0, instance, 0)
	if window == 0 {
		showMessage("Cannot create window: "+err.Error(), true)
		return
	}
	control("STATIC", "Drop preprocessed UTF-8 TXT files below, then click Start.", 0, 20, 18, 930, 24, 0)
	control("STATIC", "~2.5 MiB per part (4 MiB = 2 parts, 7 = 3, 10 = 4, 12 = 5). At least 30 chapter markers per MiB.\r\nChapter order is preserved; part sizes strictly decrease (#1 > #2 > #3...) with slight overlap if needed.", 0, 20, 48, 930, 44, 0)
	control("STATIC", "Originals leave this folder only after verification; backups are kept in .text-splitter-backup.\r\nClose the book and pause folder sync while splitting. Reading positions are not migrated.", 0, 20, 102, 930, 44, 0)
	logWindow = control("EDIT", "", 0x00b008c4, 20, 160, 925, 380, 0)
	sendMessage.Call(logWindow, 0x00c5, 0x7ffffffe, 0) // EM_SETLIMITTEXT
	oldLogProc, _, _ = user32.NewProc("SetWindowLongPtrW").Call(logWindow, ^uintptr(3), syscall.NewCallback(logProc))

	control("STATIC", "Chapter pattern (regex):", 0, 20, 562, 165, 22, 0)
	patternInput = control("EDIT", defaultChapterPattern, 0x00800080, 190, 558, 250, 28, 103)
	control("STATIC", "(Default: ^##)", 0, 450, 562, 130, 22, 0)

	startButton = control("BUTTON", "Start", 0x00010001, 710, 555, 110, 34, 101)
	clearButton = control("BUTTON", "Clear queue", 0x00010000, 835, 555, 110, 34, 102)
	enableWindow.Call(startButton, 0)
	dragAccept.Call(window, 1)
	dragAccept.Call(logWindow, 1)
	user32.NewProc("ShowWindow").Call(window, 1)
	user32.NewProc("UpdateWindow").Call(window)
	var msg message
	for {
		result, _, callErr := user32.NewProc("GetMessageW").Call(uintptr(unsafe.Pointer(&msg)), 0, 0, 0)
		if int32(result) == -1 {
			showMessage("Message loop failed: "+callErr.Error(), true)
			return
		}
		if result == 0 {
			return
		}
		user32.NewProc("TranslateMessage").Call(uintptr(unsafe.Pointer(&msg)))
		user32.NewProc("DispatchMessageW").Call(uintptr(unsafe.Pointer(&msg)))
	}
}
