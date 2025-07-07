package auth

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/hunoz/dave/cli/models"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"io"
	"net/http"
	"net/url"
	"strings"
)

type TokenResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int    `json:"expires_in"`
	TokenType    string `json:"token_type"`
	IdToken      string `json:"id_token"`
	Scope        string `json:"scope"`
}

func Authenticate(config models.AuthenticationConfig) TokenResponse {
	domain := config.Domain
	clientId := config.ClientId
	redirectURI := config.RedirectUri
	scope := strings.Join(config.Scopes, " ")
	audience := "https://dave"

	if domain[len(domain)-1] == '/' {
		domain = domain[:len(domain)-1]
	}

	verifier, challenge := generatePKCE()
	logrus.Infof("Scope: %s\n", scope)
	authURL := fmt.Sprintf("%s/authorize?audience=%s&response_type=code&client_id=%s&redirect_uri=%s&scope=%s&code_challenge=%s&code_challenge_method=S256",
		domain, url.QueryEscape(audience), clientId, url.QueryEscape(redirectURI), url.QueryEscape(scope), challenge)

	logrus.Infof("Authorization URL: %s\n", authURL)

	// Start HTTP server to handle callback
	redirectUri, _ := url.Parse(config.RedirectUri)
	port := redirectUri.Port()
	if port == "" {
		port = "8080"
	}
	codeCh := make(chan string)
	server := &http.Server{Addr: fmt.Sprintf(":%s", port)}

	http.HandleFunc("/callback", func(w http.ResponseWriter, r *http.Request) {
		query := r.URL.Query()
		code := query.Get("code")
		if code == "" {
			http.Error(w, "Missing code in response", http.StatusBadRequest)
			return
		}
		w.Write([]byte("✅ Login successful. You may close this window."))
		codeCh <- code
	})

	// Open browser
	logrus.Infoln("Opening browser for login...")
	go openBrowser(authURL)

	// Wait for code or timeout
	var code string
	go func() {
		code = <-codeCh
		server.Shutdown(context.Background())
	}()

	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		logrus.Fatalf("Error starting server: %s\n", err.Error())
	}

	// Exchange code for tokens
	tokenResp := exchangeCodeForToken(domain, clientId, redirectURI, code, verifier)

	return tokenResp
}

func exchangeCodeForToken(domain, clientID, redirectURI, code, verifier string) TokenResponse {
	tokenURL := fmt.Sprintf("%s/oauth/token", domain)
	data := url.Values{}
	data.Set("grant_type", "authorization_code")
	data.Set("client_id", clientID)
	data.Set("code", code)
	data.Set("redirect_uri", redirectURI)
	data.Set("code_verifier", verifier)

	resp, err := http.Post(tokenURL, "application/x-www-form-urlencoded", strings.NewReader(data.Encode()))
	if err != nil {
		panic(err)
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		logrus.Fatalf("Token exchange failed: %s\n", body)
	}

	logrus.Infof("Token exchange successful: %s\n", string(body))

	var result TokenResponse
	json.Unmarshal(body, &result)
	return result
}
