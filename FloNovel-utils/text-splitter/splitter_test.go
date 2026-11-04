package main

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"unicode/utf8"
)

func synthetic(size int64, chapters int) []byte {
	var result bytes.Buffer
	for index := 0; index < chapters; index++ {
		length := int(size / int64(chapters))
		if index == chapters-1 {
			length = int(size) - result.Len()
		}
		heading := fmt.Sprintf("## Chapter %d\r\nNovel text: 가나다 🙂\r\n", index+1)
		result.WriteString(heading)
		result.Write(bytes.Repeat([]byte("x"), length-len(heading)-2))
		result.WriteString("\r\n")
	}
	return result.Bytes()
}

func writeInput(t *testing.T, name string, data []byte) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), name)
	if err := os.WriteFile(path, data, 0600); err != nil {
		t.Fatal(err)
	}
	return path
}

func assertUnchanged(t *testing.T, path string, original []byte) {
	t.Helper()
	actual, err := os.ReadFile(path)
	if err != nil || !bytes.Equal(actual, original) {
		t.Fatalf("Source changed: %v", err)
	}
	matches, err := filepath.Glob(filepath.Join(filepath.Dir(path), "*#*.txt"))
	if err != nil || len(matches) != 0 {
		t.Fatalf("Unexpected outputs: %v (%v)", matches, err)
	}
}

func TestRequestedPartCounts(t *testing.T) {
	for _, item := range []struct {
		mb    int64
		parts int
	}{{7, 2}, {11, 3}, {17, 4}, {21, 5}, {10, 2}, {15, 3}} {
		t.Run(fmt.Sprintf("%dMiB", item.mb), func(t *testing.T) {
			data := synthetic(item.mb*mib, int(item.mb*64))
			size, markers, _, err := scan(bytes.NewReader(data))
			if err != nil {
				t.Fatal(err)
			}
			cuts, err := plan(size, markers)
			if err != nil || len(cuts)-1 != item.parts {
				t.Fatalf("Parts: %v, %v", cuts, err)
			}
			var prevLength int64
			for i := 0; i < len(cuts)-1; i++ {
				length := cuts[i+1] - cuts[i]
				if i > 0 && length >= prevLength {
					t.Fatalf("Parts not strictly decreasing: part %d (%d) >= part %d (%d)", i+1, length, i, prevLength)
				}
				prevLength = length
				avg := size / int64(item.parts)
				if abs(length-avg) > size/int64(len(markers))*int64(item.parts*2) {
					t.Fatalf("Unbalanced part: %d vs avg %d", length, avg)
				}
				if i > 0 && !bytes.HasPrefix(data[cuts[i]:], []byte("##")) {
					t.Fatal("Cut inside a chapter")
				}
			}
		})
	}
}

func TestSplitPreservesBytesOrderNamesAndBackup(t *testing.T) {
	data := synthetic(11*mib+1, 750)
	path := writeInput(t, "Novel 한글.TXT", data)
	result, err := split(path)
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Files) != 3 {
		t.Fatalf("Got %d parts", len(result.Files))
	}
	if _, err := os.Stat(path); !os.IsNotExist(err) {
		t.Fatalf("Original still present: %v", err)
	}
	var combined []byte
	var prevPartLen int
	for index, file := range result.Files {
		if filepath.Base(file) != fmt.Sprintf("Novel 한글#%d.txt", index+1) {
			t.Fatal(file)
		}
		part, err := os.ReadFile(file)
		if err != nil {
			t.Fatal(err)
		}
		if !utf8.Valid(part) || len(part) == 0 {
			t.Fatal("Invalid or empty part")
		}
		if index > 0 && len(part) >= prevPartLen {
			t.Fatalf("Part sizes not strictly decreasing: part #%d (%d bytes) >= part #%d (%d bytes)", index+1, len(part), index, prevPartLen)
		}
		prevPartLen = len(part)
		if index > 0 && !bytes.HasPrefix(part, []byte("##")) {
			t.Fatal("Missing heading")
		}
		combined = append(combined, part...)
	}
	if !bytes.Equal(combined, data) {
		t.Fatal("Reassembled bytes differ")
	}
	backup, err := os.ReadFile(result.Backup)
	if err != nil || !bytes.Equal(backup, data) {
		t.Fatalf("Backup differs: %v", err)
	}
	entries, err := os.ReadDir(filepath.Dir(result.Backup))
	if err != nil || len(entries) != 1 {
		t.Fatalf("Unexpected staging files: %v, %v", entries, err)
	}
}

func TestLowMarkerDensityLeavesSourceUntouched(t *testing.T) {
	data := synthetic(6*mib, 179)
	path := writeInput(t, "novel.txt", data)
	if _, err := split(path); err == nil || !strings.Contains(err.Error(), "30 markers") {
		t.Fatalf("Expected density warning: %v", err)
	}
	assertUnchanged(t, path, data)
}

func TestDensityAtExactThresholdAccepted(t *testing.T) {
	data := synthetic(6*mib, 180)
	size, markers, _, err := scan(bytes.NewReader(data))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := plan(size, markers); err != nil {
		t.Fatal(err)
	}
}

func TestSmallFilesUntouched(t *testing.T) {
	for _, size := range []int64{0, 1, partBytes} {
		t.Run(fmt.Sprint(size), func(t *testing.T) {
			data := bytes.Repeat([]byte("x"), int(size))
			path := writeInput(t, "small.txt", data)
			if _, err := split(path); err == nil {
				t.Fatal("Expected no split")
			}
			assertUnchanged(t, path, data)
		})
	}
}

func TestExistingOutputIsNeverOverwritten(t *testing.T) {
	data := synthetic(6*mib, 400)
	path := writeInput(t, "novel.txt", data)
	existing := filepath.Join(filepath.Dir(path), "novel#2.txt")
	if err := os.WriteFile(existing, []byte("KEEP"), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := split(path); err == nil {
		t.Fatal("Expected collision warning")
	}
	actual, _ := os.ReadFile(existing)
	if string(actual) != "KEEP" {
		t.Fatal("Existing output overwritten")
	}
	actual, _ = os.ReadFile(path)
	if !bytes.Equal(actual, data) {
		t.Fatal("Source modified")
	}
	if _, err := os.Stat(filepath.Join(filepath.Dir(path), "novel#1.txt")); !os.IsNotExist(err) {
		t.Fatal("Partial output created")
	}
}

func TestLiteralLineStartMarkersOnly(t *testing.T) {
	data := []byte("preamble\n ## indented\ninline ## text\n## first\r\n### second\n# nope\n##" + strings.Repeat("long heading", 10000))
	size, markers, digest, err := scan(bytes.NewReader(data))
	if err != nil {
		t.Fatal(err)
	}
	if len(markers) != 3 || size != int64(len(data)) {
		t.Fatalf("Wrong scan: %d %v", size, markers)
	}
	for _, marker := range markers {
		if !bytes.HasPrefix(data[marker:], []byte("##")) {
			t.Fatal(marker)
		}
	}
	expected := sha256.Sum256(data)
	if !bytes.Equal(expected[:], digest) {
		t.Fatal("Incorrect digest")
	}
}

func TestUTF8AcrossBufferBoundaries(t *testing.T) {
	data := []byte(strings.Repeat("x", 65535) + "🙂\n## chapter\n" + strings.Repeat("가", 50000))
	_, markers, _, err := scan(bytes.NewReader(data))
	if err != nil || len(markers) != 1 {
		t.Fatalf("Valid UTF-8 rejected: %v", err)
	}
}

func TestInvalidUTF8AndBinaryRejected(t *testing.T) {
	for index, data := range [][]byte{{0xff}, {0xf0, 0x9f}, {'#', '#', 0}, {0xff, 0xfe, '#', 0}} {
		t.Run(fmt.Sprint(index), func(t *testing.T) {
			if _, _, _, err := scan(bytes.NewReader(data)); err == nil {
				t.Fatal("Invalid input accepted")
			}
		})
	}
}

func TestUTF8BOMAndPreambleArePreserved(t *testing.T) {
	data := append([]byte("\xef\xbb\xbfPreamble\r\n"), synthetic(6*mib, 400)...)
	path := writeInput(t, "bom.txt", data)
	result, err := split(path)
	if err != nil {
		t.Fatal(err)
	}
	digest, err := hashFiles(result.Files)
	expected := sha256.Sum256(data)
	if err != nil || !bytes.Equal(digest, expected[:]) {
		t.Fatal("BOM or preamble lost")
	}
}

func TestRejectedFileDoesNotBlockNextFile(t *testing.T) {
	bad := writeInput(t, "bad.txt", synthetic(6*mib, 2))
	good := writeInput(t, "good.txt", synthetic(6*mib, 400))
	if _, err := split(bad); err == nil {
		t.Fatal("Expected rejection")
	}
	if _, err := split(good); err != nil {
		t.Fatal(err)
	}
}

func TestPublishRefusesConcurrentExistingFile(t *testing.T) {
	path := writeInput(t, "existing.txt", []byte("KEEP"))
	var created []string
	if err := publish(path, strings.NewReader("new"), 3, &created); err == nil {
		t.Fatal("Expected collision")
	}
	if len(created) != 0 {
		t.Fatal("Collision entered rollback list")
	}
	actual, _ := os.ReadFile(path)
	if string(actual) != "KEEP" {
		t.Fatal("Overwritten")
	}
}

func TestShortReadReported(t *testing.T) {
	path := filepath.Join(t.TempDir(), "short.tmp")
	if err := writeNew(path, strings.NewReader("abc"), 10); err != io.EOF {
		t.Fatalf("Expected EOF: %v", err)
	}
}

func TestRecoveryNeverOverwritesNewSource(t *testing.T) {
	backup := writeInput(t, "backup.txt", []byte("ORIGINAL"))
	source := writeInput(t, "source.txt", []byte("NEW"))
	if err := restoreOriginal(backup, source); err == nil {
		t.Fatal("Recovery overwrote new file")
	}
	actual, _ := os.ReadFile(source)
	if string(actual) != "NEW" {
		t.Fatal("New source lost")
	}
}

func TestNonTextAndDirectoryRejected(t *testing.T) {
	for _, path := range []string{writeInput(t, "file.epub", []byte("test")), t.TempDir() + ".txt"} {
		if _, err := split(path); err == nil {
			t.Fatal("Unsupported input accepted")
		}
	}
}

func TestLongOutputNameRejectedBeforeWriting(t *testing.T) {
	// NTFS allows a 255-character component: #1 fits, but #10 cannot be created.
	data := synthetic(46*mib, 3000)
	path := writeInput(t, strings.Repeat("a", 249)+".txt", data)
	if _, err := split(path); err == nil {
		t.Fatal("Expected the tenth output name to exceed the component limit")
	}
	assertUnchanged(t, path, data)
	root := filepath.Join(filepath.Dir(path), ".text-splitter-backup")
	entries, err := os.ReadDir(root)
	if !os.IsNotExist(err) || len(entries) != 0 {
		t.Fatalf("Preflight created a recovery folder: %v, %v", entries, err)
	}
}

func TestCustomChapterRegexPattern(t *testing.T) {
	var buf bytes.Buffer
	numChapters := 200
	targetSize := int64(6 * mib)
	chapLen := int(targetSize) / numChapters
	for i := 0; i < numChapters; i++ {
		heading := fmt.Sprintf("제 %d 화\r\n본문 텍스트 가나다\r\n", i+1)
		buf.WriteString(heading)
		padding := chapLen - len(heading) - 2
		if padding > 0 {
			buf.Write(bytes.Repeat([]byte("a"), padding))
		}
		buf.WriteString("\r\n")
	}
	data := buf.Bytes()
	path := writeInput(t, "CustomNovel.txt", data)

	// Default pattern "^##" should find 0 markers and fail density
	if _, err := split(path); err == nil {
		t.Fatal("Expected default pattern to fail on non-## headings")
	}

	// Custom pattern `^제\s*\d+\s*화` should succeed and split into 2 parts
	customPattern := `^제\s*\d+\s*화`
	result, err := split(path, customPattern)
	if err != nil {
		t.Fatalf("Split with custom pattern failed: %v", err)
	}
	if len(result.Files) != 2 {
		t.Fatalf("Expected 2 parts, got %d", len(result.Files))
	}

	part1, err := os.ReadFile(result.Files[0])
	if err != nil {
		t.Fatal(err)
	}
	part2, err := os.ReadFile(result.Files[1])
	if err != nil {
		t.Fatal(err)
	}
	if len(part1) <= len(part2) {
		t.Fatalf("Part sizes not decreasing: part 1 (%d) <= part 2 (%d)", len(part1), len(part2))
	}
	if !bytes.Equal(append(part1, part2...), data) {
		t.Fatal("Reassembled custom parts do not match original data")
	}
}

func TestInvalidRegexPatternReported(t *testing.T) {
	_, _, _, err := scan(strings.NewReader("some text"), "[unclosed")
	if err == nil {
		t.Fatal("Expected error for invalid regex")
	}
}

func TestFormatFailureSummary(t *testing.T) {
	// Empty list produces empty string
	if summary := formatFailureSummary(nil); summary != "" {
		t.Fatalf("Expected empty string for nil failures, got: %q", summary)
	}
	if summary := formatFailureSummary([]fileFailure{}); summary != "" {
		t.Fatalf("Expected empty string for empty failures, got: %q", summary)
	}

	// Single failure
	single := []fileFailure{
		{Path: `C:\Novels\small.txt`, Reason: "5 MiB or smaller; splitting is not needed."},
	}
	summary := formatFailureSummary(single)
	if !strings.Contains(summary, "Skipped / Failed files (1):") {
		t.Fatalf("Header count missing in summary: %s", summary)
	}
	if !strings.Contains(summary, "• small.txt: 5 MiB or smaller; splitting is not needed.") {
		t.Fatalf("Item line missing in summary: %s", summary)
	}

	// Multiple failures with duplicate filenames in different folders
	multi := []fileFailure{
		{Path: `C:\FolderA\novel.txt`, Reason: "5 MiB or smaller; splitting is not needed."},
		{Path: `C:\FolderB\novel.txt`, Reason: "Not valid UTF-8; use a preprocessed TXT file."},
		{Path: `C:\FolderC\other.txt`, Reason: "Output already exists: other#1.txt"},
	}
	multiSummary := formatFailureSummary(multi)
	if !strings.Contains(multiSummary, "Skipped / Failed files (3):") {
		t.Fatalf("Header count (3) missing: %s", multiSummary)
	}
	if !strings.Contains(multiSummary, "• novel.txt (FolderA): 5 MiB or smaller; splitting is not needed.") {
		t.Fatalf("Disambiguated FolderA missing: %s", multiSummary)
	}
	if !strings.Contains(multiSummary, "• novel.txt (FolderB): Not valid UTF-8; use a preprocessed TXT file.") {
		t.Fatalf("Disambiguated FolderB missing: %s", multiSummary)
	}
	if !strings.Contains(multiSummary, "• other.txt: Output already exists: other#1.txt") {
		t.Fatalf("Undisambiguated other.txt missing: %s", multiSummary)
	}
}


