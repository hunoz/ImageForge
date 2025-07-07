package cmd

import (
	"github.com/hunoz/dave/cli/cmd/actions"
	"github.com/hunoz/dave/cli/models"
	"github.com/pkg/errors"
	"github.com/spf13/cobra"
)

var requiresAuthCommands = cobra.Group{
	ID:    "requires-auth",
	Title: "Commands which require authentication",
}

var RootCmd = &cobra.Command{
	Use:   "dave",
	Short: "Dave is a CLI used for connecting to and managing your Dave developer workspaces",
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		if cmd.Name() == actions.InitCmd.Name() {
			return nil
		}

		reader := models.NewConfigReader()
		if !reader.Exists() {
			return errors.New("Dave has not been initialized. Please run `dave init`")
		}

		if cmd.GroupID == "requires-auth" {
			actions.AuthenticateIfNotAuthenticated(models.NewCacheReader())
		}

		return nil
	},
}

func init() {
	RootCmd.AddCommand(actions.ConnectCmd)
	RootCmd.AddCommand(actions.InitCmd)
	RootCmd.AddGroup(&requiresAuthCommands)
}
