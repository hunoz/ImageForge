package actions

import (
	"github.com/spf13/cobra"
	"github.com/spf13/viper"
	"os/user"
)

var connectViper = viper.New()

var ConnectCmd = &cobra.Command{
	Use:     "connect",
	Short:   "Connect to a Dave workspace",
	GroupID: "requires-auth",
	Run: func(cmd *cobra.Command, args []string) {
		//workspaceName := viper.GetString("workspace")
		//username := viper.GetString("username")
	},
}

func init() {
	user, _ := user.Current()
	ConnectCmd.Flags().StringP("workspace", "w", "", "The workspace to connect to")
	ConnectCmd.MarkFlagRequired("workspace")

	ConnectCmd.Flags().StringP("username", "u", user.Username, "The username to connect with")
	ConnectCmd.MarkFlagRequired("username")

	viper.BindPFlags(ConnectCmd.Flags())
}
