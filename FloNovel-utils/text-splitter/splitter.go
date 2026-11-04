package main

import (
	"bufio"
	"bytes"
	"crypto/sha256"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"unicode/utf8"
)

const mib int64 = 1024 * 1024
const partBytes = 5 * mib
const defaultChapterPattern = "^##"

type splitResult struct {
	Files  []string
	Backup string
}

type fileFailure struct {
	Path   string
	Reason string
}

func formatFailureSummary(failures []fileFailure) string {
	if len(failures) == 0 {
		return ""
	}
	counts := make(map[string]int)
	for _, f := range failures {
		name := filepath.Base(f.Path)
		counts[name]++
	}

	var sb strings.Builder
	sb.WriteString("\r\n--------------------------------------------------\r\n")
	sb.WriteString(fmt.Sprintf("Skipped / Failed files (%d):\r\n", len(failures)))
	for _, f := range failures {
		name := filepath.Base(f.Path)
		if counts[name] > 1 {
			parent := filepath.Base(filepath.Dir(f.Path))
			if parent != "" && parent != "." && parent != string(filepath.Separator) {
				name = fmt.Sprintf("%s (%s)", name, parent)
			} else {
				name = fmt.Sprintf("%s (%s)", name, f.Path)
			}
		}
		sb.WriteString(fmt.Sprintf("  • %s: %s\r\n", name, f.Reason))
	}
	sb.WriteString("--------------------------------------------------\r\n")
	return sb.String()
}

// scan keeps memory independent of line length and recognizes the specified regex pattern (default ^##).
func scan(input io.Reader, pattern ...string) (size int64, markers []int64, digest []byte, err error) {
	pat := defaultChapterPattern
	if len(pattern) > 0 && strings.TrimSpace(pattern[0]) != "" {
		pat = strings.TrimSpace(pattern[0])
	}
	re, err := regexp.Compile(pat)
	if err != nil {
		return 0, nil, nil, fmt.Errorf("Invalid chapter regex pattern %q: %w", pat, err)
	}
	isDefault := pat == defaultChapterPattern

	hash := sha256.New()
	reader := bufio.NewReaderSize(io.TeeReader(input, hash), 64*1024)
	buffer := make([]byte, 64*1024)
	var tail []byte
	var lineStart int64
	var linePrefix []byte

	checkLine := func() {
		lineText := bytes.TrimRight(linePrefix, "\r")
		matched := false
		if isDefault {
			matched = len(lineText) >= 2 && lineText[0] == '#' && lineText[1] == '#'
		} else {
			matched = re.Match(lineText)
		}
		if matched {
			markers = append(markers, lineStart)
		}
	}

	for {
		n, readErr := reader.Read(buffer)
		if n > 0 {
			data := append(tail, buffer[:n]...)
			end := len(data)
			start := end - 1
			for start > 0 && data[start]&0xc0 == 0x80 {
				start--
			}
			if !utf8.FullRune(data[start:]) {
				end = start
			}
			if !utf8.Valid(data[:end]) {
				return 0, nil, nil, errors.New("Not valid UTF-8; use a preprocessed TXT file.")
			}
			tail = append([]byte(nil), data[end:]...)
			for _, b := range buffer[:n] {
				if b == 0 {
					return 0, nil, nil, errors.New("NUL bytes found; UTF-16 and binary files are not supported.")
				}
				if b == '\n' {
					checkLine()
					lineStart = size + 1
					linePrefix = linePrefix[:0]
				} else {
					if len(linePrefix) < 1024 {
						linePrefix = append(linePrefix, b)
					}
				}
				size++
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return 0, nil, nil, readErr
		}
	}
	if len(tail) != 0 {
		return 0, nil, nil, errors.New("Incomplete UTF-8 character at end of file.")
	}
	if len(linePrefix) > 0 {
		checkLine()
	}
	return size, markers, hash.Sum(nil), nil
}

func plan(size int64, markers []int64) ([]int64, error) {
	if size <= partBytes {
		return nil, errors.New("5 MiB or smaller; splitting is not needed.")
	}
	if float64(len(markers))*float64(mib) < float64(size)*30 {
		return nil, fmt.Errorf("Only %d chapter markers in %.2f MiB; at least 30 markers per MiB are required.", len(markers), float64(size)/float64(mib))
	}
	parts := int((size-1)/partBytes + 1)
	candidates := make([]int64, 0, len(markers))
	for _, marker := range markers {
		if marker > 0 && marker < size {
			candidates = append(candidates, marker)
		}
	}
	if len(candidates) < parts-1 {
		return nil, errors.New("Not enough chapter boundaries.")
	}

	cuts, err := findDescendingCuts(size, candidates, parts)
	if err != nil {
		return nil, err
	}
	return cuts, nil
}

func findDescendingCuts(size int64, candidates []int64, k int) ([]int64, error) {
	if k == 2 {
		half := size / 2
		idx := sort.Search(len(candidates), func(i int) bool {
			return candidates[i] > half
		})
		if idx < len(candidates) {
			cut := candidates[idx]
			if cut > 0 && cut < size && cut > size-cut {
				return []int64{0, cut, size}, nil
			}
		}
		return nil, errors.New("Cannot find chapter boundary for descending part sizes (#1 > #2).")
	}

	avgChapter := size / int64(len(candidates))
	if avgChapter < 1 {
		avgChapter = 1
	}
	step := avgChapter * 2
	if step < 16384 {
		step = 16384
	}
	avgPart := size / int64(k)
	if avgPart-int64(k-1)*step <= 0 {
		step = avgPart / int64(k*2)
		if step < 1024 {
			step = 1024
		}
	}

	targetCuts := make([]int64, k-1)
	var accum int64
	for j := 1; j < k; j++ {
		tJ := avgPart + int64((float64(k-1)/2.0-float64(j-1))*float64(step))
		accum += tJ
		targetCuts[j-1] = accum
	}

	cuts := make([]int64, 0, k+1)
	cuts = append(cuts, 0)
	candIdx := 0
	for part := 1; part < k; part++ {
		target := targetCuts[part-1]
		lastIdx := len(candidates) - (k - part)
		count := lastIdx - candIdx + 1
		idx := candIdx + sort.Search(count, func(i int) bool {
			return candidates[candIdx+i] >= target
		})
		if idx > lastIdx {
			idx = lastIdx
		}
		if idx > candIdx && abs(candidates[idx-1]-target) < abs(candidates[idx]-target) {
			idx--
		}
		cuts = append(cuts, candidates[idx])
		candIdx = idx + 1
	}
	cuts = append(cuts, size)

	candMap := make(map[int64]int, len(candidates))
	for idx, c := range candidates {
		candMap[c] = idx
	}

	for pass := 0; pass < 50; pass++ {
		adjusted := false
		for j := 1; j < k; j++ {
			lPrev := cuts[j] - cuts[j-1]
			lNext := cuts[j+1] - cuts[j]
			if lPrev <= lNext {
				idx, ok := candMap[cuts[j]]
				if !ok {
					continue
				}
				nextCut := cuts[j+1]
				shifted := false
				for newIdx := idx + 1; newIdx < len(candidates); newIdx++ {
					newC := candidates[newIdx]
					if newC >= nextCut {
						break
					}
					newLPrev := newC - cuts[j-1]
					newLNext := nextCut - newC
					if j > 1 && newLPrev >= cuts[j-1]-cuts[j-2] {
						break
					}
					cuts[j] = newC
					shifted = true
					adjusted = true
					if newLPrev > newLNext {
						break
					}
				}
				if !shifted && j > 1 {
					prevPrevCut := cuts[j-2]
					prevIdx, prevOk := candMap[cuts[j-1]]
					if prevOk {
						for newIdx := prevIdx - 1; newIdx >= 0; newIdx-- {
							newC := candidates[newIdx]
							if newC <= prevPrevCut {
								break
							}
							cuts[j-1] = newC
							adjusted = true
							break
						}
					}
				}
			}
		}
		if !adjusted {
			break
		}
	}

	for i := 1; i < len(cuts)-1; i++ {
		lPrev := cuts[i] - cuts[i-1]
		lNext := cuts[i+1] - cuts[i]
		if lPrev <= lNext {
			return nil, errors.New("Cannot find chapter boundaries that satisfy descending part sizes (#1 > #2 > ...).")
		}
	}
	return cuts, nil
}

func abs(value int64) int64 {
	if value < 0 {
		return -value
	}
	return value
}

func split(path string, pattern ...string) (result splitResult, err error) {
	source, err := filepath.Abs(path)
	if err != nil {
		return result, err
	}
	if !strings.EqualFold(filepath.Ext(source), ".txt") {
		return result, errors.New("Only TXT files are supported.")
	}
	info, err := os.Lstat(source)
	if err != nil {
		return result, err
	}
	if !info.Mode().IsRegular() {
		return result, errors.New("Only regular, non-linked files are supported.")
	}
	file, err := os.Open(source)
	if err != nil {
		return result, err
	}
	defer file.Close()
	size, markers, originalHash, err := scan(file, pattern...)
	if err != nil {
		return result, err
	}
	cuts, err := plan(size, markers)
	if err != nil {
		return result, err
	}
	stem := strings.TrimSuffix(source, filepath.Ext(source))
	for index := 1; index < len(cuts); index++ {
		output := fmt.Sprintf("%s#%d.txt", stem, index)
		if _, statErr := os.Lstat(output); !os.IsNotExist(statErr) {
			if statErr != nil {
				return result, statErr
			}
			return result, fmt.Errorf("Output already exists: %s", output)
		}
		result.Files = append(result.Files, output)
	}
	parent := filepath.Dir(source)
	backupRoot := filepath.Join(parent, ".text-splitter-backup")
	resolvedParent, err := filepath.EvalSymlinks(parent)
	if err != nil {
		return result, err
	}
	if !strings.EqualFold(filepath.Clean(resolvedParent), filepath.Clean(parent)) {
		return result, errors.New("Linked parent folders are not supported.")
	}
	if err = os.Mkdir(backupRoot, 0700); err != nil && !os.IsExist(err) {
		return result, err
	}
	resolvedBackup, err := filepath.EvalSymlinks(backupRoot)
	if err != nil {
		return result, err
	}
	if !strings.EqualFold(resolvedBackup, backupRoot) {
		return result, errors.New("Linked backup folders are not supported.")
	}
	work, err := os.MkdirTemp(backupRoot, "split-")
	if err != nil {
		return result, err
	}
	result.Backup = filepath.Join(work, filepath.Base(source))
	var created []string
	var staged []string
	defer func() {
		for _, temp := range staged {
			if e := os.Remove(temp); e != nil && !os.IsNotExist(e) {
				err = errors.Join(err, e)
			}
		}
		if err != nil {
			for _, output := range created {
				if e := os.Remove(output); e != nil {
					err = errors.Join(err, fmt.Errorf("Cleanup %s: %w", output, e))
				}
			}
			if e := os.Remove(work); e != nil && !os.IsNotExist(e) {
				err = errors.Join(err, fmt.Errorf("Retained recovery folder %s: %w", work, e))
			}
		}
	}()
	for index := range result.Files {
		temp := filepath.Join(work, fmt.Sprintf("part-%d.tmp", index+1))
		staged = append(staged, temp)
		length := cuts[index+1] - cuts[index]
		if err = writeNew(temp, io.NewSectionReader(file, cuts[index], length), length); err != nil {
			return result, err
		}
	}
	for index, output := range result.Files {
		stagedFile, openErr := os.Open(staged[index])
		if openErr != nil {
			return result, openErr
		}
		// Reserve each destination exclusively: never overwrite existing library files.
		err = publish(output, stagedFile, cuts[index+1]-cuts[index], &created)
		closeErr := stagedFile.Close()
		if err != nil || closeErr != nil {
			return result, errors.Join(err, closeErr)
		}
	}
	outputHash, err := hashFiles(result.Files)
	if err != nil {
		return result, err
	}
	currentHash, err := hashFiles([]string{source})
	if err != nil {
		return result, err
	}
	currentInfo, err := os.Stat(source)
	if err != nil {
		return result, err
	}
	if !os.SameFile(info, currentInfo) || !bytes.Equal(originalHash, currentHash) || !bytes.Equal(originalHash, outputHash) {
		return result, errors.New("Source changed or output verification failed; source retained.")
	}
	if err = file.Close(); err != nil {
		return result, err
	}
	if err = os.Rename(source, result.Backup); err != nil {
		return result, err
	}
	backupHash, err := hashFiles([]string{result.Backup})
	if err != nil || !bytes.Equal(originalHash, backupHash) {
		restoreErr := restoreOriginal(result.Backup, source)
		return result, errors.Join(errors.New("Source changed during finalization; check the recovery folder."), err, restoreErr)
	}
	return result, nil
}

func publish(path string, input io.Reader, size int64, created *[]string) error {
	output, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err != nil {
		return err
	}
	*created = append(*created, path)
	_, copyErr := io.CopyN(output, input, size)
	syncErr := output.Sync()
	closeErr := output.Close()
	if syncErr == nil && closeErr == nil {
		return copyErr
	}
	return errors.Join(copyErr, syncErr, closeErr)
}

func writeNew(path string, input io.Reader, size int64) error {
	var created []string
	return publish(path, input, size, &created)
}

func restoreOriginal(backup, source string) error {
	// A concurrent new file at the source name must also survive recovery.
	file, err := os.Open(backup)
	if err != nil {
		return err
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		return err
	}
	return writeNew(source, file, info.Size())
}

func hashFiles(paths []string) ([]byte, error) {
	hash := sha256.New()
	for _, path := range paths {
		file, err := os.Open(path)
		if err != nil {
			return nil, err
		}
		_, err = io.Copy(hash, file)
		closeErr := file.Close()
		if err != nil || closeErr != nil {
			return nil, errors.Join(err, closeErr)
		}
	}
	return hash.Sum(nil), nil
}
