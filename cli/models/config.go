package models

import (
	"encoding/json"
	"fmt"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
	"os"
	"strings"
	"time"
)

const ConfigFilename = "config.json"
const CacheFilename = "cache.json"

var ActiveConfig Config
var ActiveCache Cache

type AuthenticationConfig struct {
	Domain      string   `json:"domain"`
	ClientId    string   `json:"clientId"`
	Scopes      []string `json:"scopes"`
	RedirectUri string   `json:"redirectUri"`
}

type Config struct {
	Authentication AuthenticationConfig `json:"authentication"`
}

type ConfigReader struct {
	filePath string
}

func (cr ConfigReader) Read() (*Config, error) {
	data, err := readFile(cr.filePath)
	if err != nil {
		if os.IsNotExist(err) {
			return &Config{
				Authentication: AuthenticationConfig{
					Domain:      "",
					ClientId:    "",
					Scopes:      []string{},
					RedirectUri: "",
				},
			}, nil
		} else {
			return nil, errors.Wrap(err, "Failed to read config")
		}
	}

	var config Config
	err = json.Unmarshal(data, &config)
	if err != nil {
		return nil, errors.Wrap(err, "Failed to read config")
	}

	return &config, nil
}

func (cr ConfigReader) Write(data Config) error {
	marshalled, err := json.Marshal(data)
	if err != nil {
		return errors.Wrap(err, "Failed to marshal config")
	}

	return writeFile(cr.filePath, marshalled)
}

func (cr ConfigReader) Delete() error {
	return os.Remove(cr.filePath)
}

func (cr ConfigReader) Exists() bool {
	_, err := os.Stat(cr.filePath)
	return err == nil || os.IsExist(err)
}

func NewConfigReader() ConfigReader {
	configFilepath := strings.Join([]string{getDirectory(), ConfigFilename}, string(os.PathSeparator))
	reader := ConfigReader{
		filePath: configFilepath,
	}

	if _, err := os.Stat(reader.filePath); os.IsNotExist(err) {
		_ = os.MkdirAll(getParentDirectory(configFilepath), 0755)
	}

	return reader
}

type Cache struct {
	IdToken      *string   `json:"idToken"`
	AccessToken  *string   `json:"accessToken"`
	RefreshToken *string   `json:"refreshToken"`
	ExpiresAt    time.Time `json:"expiresAt"`
}

type CacheReader struct {
	filePath string
}

func (cr CacheReader) Read() (*Cache, error) {
	data, err := readFile(cr.filePath)
	if err != nil {
		if os.IsNotExist(err) {
			return &Cache{
				IdToken:      nil,
				AccessToken:  nil,
				RefreshToken: nil,
				ExpiresAt:    time.Now().Add(-5 * time.Minute),
			}, nil
		}
		return nil, errors.Wrap(err, "Failed to read config")
	}

	var cache Cache
	err = json.Unmarshal(data, &cache)
	if err != nil {
		return nil, errors.Wrap(err, "Failed to read cache")
	}

	return &cache, nil
}

func (cr CacheReader) Write(data Cache) error {
	marshalled, err := json.Marshal(data)
	if err != nil {
		return errors.Wrap(err, "Failed to marshal cache")
	}

	return writeFile(cr.filePath, marshalled)
}

func (cr CacheReader) Delete() error {
	return os.Remove(cr.filePath)
}

func (cr CacheReader) Exists() bool {
	_, err := os.Stat(cr.filePath)
	return err == nil || os.IsExist(err)
}

func NewCacheReader() CacheReader {
	cacheFilepath := strings.Join([]string{getDirectory(), CacheFilename}, string(os.PathSeparator))
	reader := CacheReader{
		filePath: cacheFilepath,
	}

	if _, err := os.Stat(reader.filePath); os.IsNotExist(err) {
		_ = os.MkdirAll(getParentDirectory(cacheFilepath), 0755)
	}

	return reader
}

func getParentDirectory(path string) string {
	pathSplit := strings.Split(path, string(os.PathSeparator))
	return strings.Join(pathSplit[:len(pathSplit)-1], string(os.PathSeparator))
}

func getDirectory() string {
	var err error

	home, err := os.UserHomeDir()
	if err != nil {
		logrus.Fatalf("Failed to get home directory: %s\n", err.Error())
	}

	return strings.Join([]string{home, ".dave"}, string(os.PathSeparator))
}

func readFile(filename string) ([]byte, error) {
	return os.ReadFile(filename)
}

func writeFile(filename string, data []byte) error {
	if err := os.MkdirAll(getDirectory(), 0600); err != nil {
		return errors.Wrap(err, fmt.Sprintf("Error creating file %s", filename))
	}

	return os.WriteFile(filename, data, 0600)
}

func init() {
	config, err := NewConfigReader().Read()
	if err != nil {
		logrus.Fatalf("Failed to read config: %s\n", err)
	}

	ActiveConfig = *config

	cache, err := NewCacheReader().Read()
	if err != nil {
		logrus.Fatalf("Failed to read cache: %s\n", err)
	}

	ActiveCache = *cache
}
