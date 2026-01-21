package config

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v3"
)

type Config struct {
	Server        ServerConfig        `yaml:"server"`
	MySQL         MySQLConfig         `yaml:"mysql"`
	Redis         RedisConfig         `yaml:"redis"`
	Kafka         KafkaConfig         `yaml:"kafka"`
	MinIO         MinIOConfig         `yaml:"minio"`
	Elasticsearch ElasticsearchConfig `yaml:"elasticsearch"`
	DeepSeek      DeepSeekConfig      `yaml:"deepseek"`
	Embedding     EmbeddingConfig     `yaml:"embedding"`
	JWT           JWTConfig           `yaml:"jwt"`
	Admin         AdminConfig         `yaml:"admin"`
	File          FileConfig          `yaml:"file"`
	AI            AIConfig            `yaml:"ai"`
}

type ServerConfig struct {
	Port int `yaml:"port"`
}

type MySQLConfig struct {
	Host         string `yaml:"host"`
	Port         int    `yaml:"port"`
	Database     string `yaml:"database"`
	Username     string `yaml:"username"`
	Password     string `yaml:"password"`
	Charset      string `yaml:"charset"`
	MaxIdleConns int    `yaml:"max_idle_conns"`
	MaxOpenConns int    `yaml:"max_open_conns"`
}

func (c MySQLConfig) DSN() string {
	return fmt.Sprintf("%s:%s@tcp(%s:%d)/%s?charset=%s&parseTime=True&loc=Local",
		c.Username, c.Password, c.Host, c.Port, c.Database, c.Charset)
}

type RedisConfig struct {
	Host     string `yaml:"host"`
	Port     int    `yaml:"port"`
	Password string `yaml:"password"`
	DB       int    `yaml:"db"`
}

func (c RedisConfig) Addr() string {
	return fmt.Sprintf("%s:%d", c.Host, c.Port)
}

type KafkaConfig struct {
	Brokers  []string `yaml:"brokers"`
	Topic    string   `yaml:"topic"`
	DLTTopic string   `yaml:"dlt_topic"`
	GroupID  string   `yaml:"group_id"`
}

type MinIOConfig struct {
	Endpoint  string `yaml:"endpoint"`
	AccessKey string `yaml:"access_key"`
	SecretKey string `yaml:"secret_key"`
	Bucket    string `yaml:"bucket"`
	PublicURL string `yaml:"public_url"`
	UseSSL    bool   `yaml:"use_ssl"`
}

type ElasticsearchConfig struct {
	Host      string `yaml:"host"`
	Port      int    `yaml:"port"`
	Scheme    string `yaml:"scheme"`
	Username  string `yaml:"username"`
	Password  string `yaml:"password"`
	IndexName string `yaml:"index_name"`
}

func (c ElasticsearchConfig) URL() string {
	return fmt.Sprintf("%s://%s:%d", c.Scheme, c.Host, c.Port)
}

type DeepSeekConfig struct {
	URL    string `yaml:"url"`
	Model  string `yaml:"model"`
	APIKey string `yaml:"api_key"`
}

type EmbeddingConfig struct {
	URL       string `yaml:"url"`
	Model     string `yaml:"model"`
	APIKey    string `yaml:"api_key"`
	Dimension int    `yaml:"dimension"`
	BatchSize int    `yaml:"batch_size"`
}

type JWTConfig struct {
	Secret        string `yaml:"secret"`
	AccessExpire  int    `yaml:"access_expire"`
	RefreshExpire int    `yaml:"refresh_expire"`
}

type AdminConfig struct {
	Username   string `yaml:"username"`
	Password   string `yaml:"password"`
	PrimaryOrg string `yaml:"primary_org"`
	Tags       string `yaml:"tags"`
}

type FileConfig struct {
	MaxFileSize     int64   `yaml:"max_file_size"`
	MaxRequestSize  int64   `yaml:"max_request_size"`
	ChunkSize       int     `yaml:"chunk_size"`
	BufferSize      int     `yaml:"buffer_size"`
	MemoryThreshold float64 `yaml:"memory_threshold"`
}

type AIConfig struct {
	SystemPrompt string  `yaml:"system_prompt"`
	Temperature  float64 `yaml:"temperature"`
	MaxTokens    int     `yaml:"max_tokens"`
	TopP         float64 `yaml:"top_p"`
	RefStart     string  `yaml:"ref_start"`
	RefEnd       string  `yaml:"ref_end"`
}

var AppConfig *Config

func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read config file: %w", err)
	}
	var cfg Config
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("parse config file: %w", err)
	}
	AppConfig = &cfg
	return &cfg, nil
}
