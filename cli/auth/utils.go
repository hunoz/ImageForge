package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"os/exec"
	"runtime"
)

func generatePKCE() (string, string) {
	verifierBytes := make([]byte, 32)
	_, _ = rand.Read(verifierBytes)
	verifier := base64.URLEncoding.WithPadding(base64.NoPadding).EncodeToString(verifierBytes)
	sum := sha256.Sum256([]byte(verifier))
	challenge := base64.URLEncoding.WithPadding(base64.NoPadding).EncodeToString(sum[:])
	return verifier, challenge
}

func openBrowser(url string) {
	var cmd string
	var args []string
	switch {
	case "windows" == runtime.GOOS:
		cmd = "rundll32"
		args = []string{"url.dll,FileProtocolHandler", url}
	case "darwin" == runtime.GOOS:
		cmd = "open"
		args = []string{url}
	default:
		cmd = "xdg-open"
		args = []string{url}
	}
	exec.Command(cmd, args...).Start()
}
