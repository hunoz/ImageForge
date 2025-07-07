package main

import (
	"github.com/hunoz/dave/cli/cmd"
	"github.com/sirupsen/logrus"
)

func main() {
	if err := cmd.RootCmd.Execute(); err != nil {
		logrus.Fatalf("Error: %s\n", err.Error())
	}
}
