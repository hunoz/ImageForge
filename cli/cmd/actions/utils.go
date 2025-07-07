package actions

import (
	"github.com/hunoz/dave/cli/auth"
	"github.com/hunoz/dave/cli/models"
	"github.com/sirupsen/logrus"
	"time"
)

func AuthenticateIfNotAuthenticated(cacheReader models.CacheReader) {
	if models.ActiveCache.IdToken == nil || models.ActiveCache.ExpiresAt.Before(time.Now()) {
		tokenResponse := auth.Authenticate(models.ActiveConfig.Authentication)

		models.ActiveCache.IdToken = &tokenResponse.IdToken
		models.ActiveCache.ExpiresAt = time.Now().Add(time.Duration(tokenResponse.ExpiresIn) * time.Second)
		models.ActiveCache.AccessToken = &tokenResponse.AccessToken
		models.ActiveCache.RefreshToken = &tokenResponse.RefreshToken

		if err := cacheReader.Write(models.ActiveCache); err != nil {
			logrus.Fatalf("Error writing cache: %s\n", err.Error())
		}
	}
}
