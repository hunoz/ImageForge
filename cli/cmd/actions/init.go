package actions

import (
	"github.com/hunoz/dave/cli/models"
	"github.com/manifoldco/promptui"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
	"net/url"
	"strings"
)

var initFlagKey struct {
	Domain      string
	ClientId    string
	Scopes      string
	RedirectUri string
} = struct {
	Domain      string
	ClientId    string
	Scopes      string
	RedirectUri string
}{
	Domain:      "domain",
	ClientId:    "clientId",
	Scopes:      "scopes",
	RedirectUri: "redirectUri",
}

var InitCmd = &cobra.Command{
	Use:   "init",
	Short: "Initialize the Dave CLI",
	Run: func(cmd *cobra.Command, args []string) {
		reader := models.NewConfigReader()
		config := models.ActiveConfig

		if domainFlag, _ := cmd.Flags().GetString(initFlagKey.Domain); domainFlag == "" {
			domainPrompt := promptui.Prompt{
				Label:     "Authenticator Domain",
				Default:   config.Authentication.Domain,
				AllowEdit: true,
				Validate: func(s string) error {
					if len(strings.TrimSpace(s)) == 0 {
						return errors.New("Domain cannot be empty")
					}

					parsed, err := url.Parse(s)
					if err != nil {
						return errors.New("Invalid URL for domain")
					}

					if parsed.Scheme != "" {
						return errors.New("Domain must not include scheme")
					}

					if parsed.Port() != "" {
						return errors.New("Domain must not include port")
					}

					if parsed.Query().Encode() != "" {
						return errors.New("Domain must not include query")
					}

					return nil
				},
			}

			d, err := domainPrompt.Run()
			if err != nil {
				logrus.Fatalf("Error getting domain: %s\n", err.Error())
			}

			config.Authentication.Domain = d
		}

		if clientIdFlag, _ := cmd.Flags().GetString(initFlagKey.ClientId); clientIdFlag == "" {
			clientIdPrompt := promptui.Prompt{
				Label:     "Authenticator Client ID",
				Default:   config.Authentication.ClientId,
				AllowEdit: true,
				Validate: func(s string) error {
					if len(strings.TrimSpace(s)) == 0 {
						return errors.New("Client ID cannot be empty")
					}

					return nil
				},
			}

			id, err := clientIdPrompt.Run()
			if err != nil {
				logrus.Fatalf("Error getting client ID: %s\n", err.Error())
			}

			config.Authentication.ClientId = id
		}

		if scopesFlag, _ := cmd.Flags().GetStringArray(initFlagKey.Scopes); len(scopesFlag) == 0 {
			defaultScopes := config.Authentication.Scopes
			if len(defaultScopes) == 0 {
				defaultScopes = []string{"openid", "profile", "email", "offline_access"}
			}
			scopesPrompt := promptui.Prompt{
				Label:     "Authenticator Scopes",
				Default:   strings.Join(defaultScopes, " "),
				AllowEdit: true,
				Validate: func(s string) error {
					if len(s) == 0 {
						return nil
					}
					scopes := strings.Split(s, " ")
					if len(scopes) == 0 {
						return errors.New("Scopes cannot be empty")
					}

					return nil
				},
			}

			s, err := scopesPrompt.Run()
			if err != nil {
				logrus.Fatalf("Error getting scopes: %s\n", err.Error())
			}

			config.Authentication.Scopes = strings.Split(s, " ")
		}

		if redirectUriFlag, _ := cmd.Flags().GetString(initFlagKey.RedirectUri); redirectUriFlag == "" {
			redirectUriPrompt := promptui.Prompt{
				Label:     "Authenticator Redirect URI",
				Default:   config.Authentication.RedirectUri,
				AllowEdit: true,
				Validate: func(s string) error {
					if len(s) == 0 {
						return nil
					}
					_, err := url.Parse(s)
					if err != nil {
						return errors.New("Invalid URL for redirect URI")
					}

					return nil
				},
			}

			uri, err := redirectUriPrompt.Run()
			if err != nil {
				logrus.Fatalf("Error getting client ID: %s\n", err.Error())
			}

			config.Authentication.RedirectUri = uri
		}

		err := reader.Write(config)
		if err != nil {
			logrus.Fatalf("Error writing config: %s\n", err.Error())
		}
	},
}

func init() {
	InitCmd.Flags().StringP(initFlagKey.Domain, "d", "", "The domain of the authentication provider")
	InitCmd.Flags().StringP(initFlagKey.ClientId, "c", "", "The client id of the authentication provider")
	InitCmd.Flags().StringArrayP(initFlagKey.Scopes, "s", []string{}, "The scopes of the authentication provider")
	InitCmd.Flags().StringP(initFlagKey.RedirectUri, "r", "", "The redirect uri of the authentication provider")
}
