//go:build windows

package main

import (
	"encoding/binary"
	"os"
	"path/filepath"
	"syscall"
	"testing"
	"unsafe"
)

func TestNativeMultiFileDropOnlyQueuesAndDeduplicates(t *testing.T) {
	first := filepath.Join(t.TempDir(), "First 한글.txt")
	second := filepath.Join(t.TempDir(), "Second.txt")
	paths := []string{first, second, first}
	var names []uint16
	for _, path := range paths {
		encoded, err := syscall.UTF16FromString(path)
		if err != nil {
			t.Fatal(err)
		}
		names = append(names, encoded...)
	}
	names = append(names, 0)
	// Construct the documented in-memory DROPFILES payload, without controlling any real window.
	handle, _, err := kernel32.NewProc("GlobalAlloc").Call(0x42, uintptr(20+2*len(names)))
	if handle == 0 {
		t.Fatal(err)
	}
	pointer, _, err := kernel32.NewProc("GlobalLock").Call(handle)
	if pointer == 0 {
		kernel32.NewProc("GlobalFree").Call(handle)
		t.Fatal(err)
	}
	payload := make([]byte, 20+2*len(names))
	binary.LittleEndian.PutUint32(payload[0:4], 20)
	binary.LittleEndian.PutUint32(payload[16:20], 1)
	for index, char := range names {
		binary.LittleEndian.PutUint16(payload[20+2*index:], char)
	}
	kernel32.NewProc("RtlMoveMemory").Call(pointer, uintptr(unsafe.Pointer(&payload[0])), uintptr(len(payload)))
	kernel32.NewProc("GlobalUnlock").Call(handle)
	queue, logText, busy, completed = nil, "", false, false
	t.Cleanup(func() { queue, logText, busy, completed = nil, "", false, false })
	acceptDrop(handle)
	if len(queue) != 2 || queue[0] != first || queue[1] != second {
		t.Fatalf("Wrong queue: %v", queue)
	}
	if busy {
		t.Fatal("Dropping files started work without Start")
	}
	if len(updates) != 0 {
		t.Fatal("Dropping files produced processing updates")
	}
}

func TestLockedOriginalRollsBackAllOutputs(t *testing.T) {
	data := synthetic(6*mib, 400)
	path := writeInput(t, "locked.txt", data)
	name, err := syscall.UTF16PtrFromString(path)
	if err != nil {
		t.Fatal(err)
	}
	// Simulate another application holding the source open without delete sharing.
	handle, err := syscall.CreateFile(name, syscall.GENERIC_READ,
		syscall.FILE_SHARE_READ|syscall.FILE_SHARE_WRITE, nil,
		syscall.OPEN_EXISTING, syscall.FILE_ATTRIBUTE_NORMAL, 0)
	if err != nil {
		t.Fatal(err)
	}
	defer syscall.CloseHandle(handle)
	if _, err := split(path); err == nil {
		t.Fatal("Expected source rename to be blocked")
	}
	assertUnchanged(t, path, data)
	entries, err := os.ReadDir(filepath.Join(filepath.Dir(path), ".text-splitter-backup"))
	if err != nil || len(entries) != 0 {
		t.Fatalf("Recovery staging retained: %v, %v", entries, err)
	}
}
