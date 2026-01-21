package consumer

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"strings"
	"time"

	"goDemo/internal/model"
	"goDemo/internal/service"
	"goDemo/internal/utils"

	"github.com/segmentio/kafka-go"
)

// FileProcessingConsumer Kafka文件处理消费者
type FileProcessingConsumer struct {
	reader             *kafka.Reader
	parseService       *service.ParseService
	vectorizationSvc   *service.VectorizationService
}

func NewFileProcessingConsumer(
	brokers []string, topic, groupID string,
	parseService *service.ParseService,
	vectorizationSvc *service.VectorizationService,
) *FileProcessingConsumer {
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:  brokers,
		Topic:    topic,
		GroupID:  groupID,
		MinBytes: 10e3,
		MaxBytes: 10e6,
	})

	return &FileProcessingConsumer{
		reader:           reader,
		parseService:     parseService,
		vectorizationSvc: vectorizationSvc,
	}
}

// Start 启动消费
func (c *FileProcessingConsumer) Start(ctx context.Context) {
	utils.BusinessLog("Kafka消费者启动, 监听文件处理消息...")

	go func() {
		for {
			select {
			case <-ctx.Done():
				c.reader.Close()
				return
			default:
				m, err := c.reader.ReadMessage(ctx)
				if err != nil {
					if ctx.Err() != nil {
						return
					}
					utils.ErrorLog("Kafka读取消息失败: %v", err)
					time.Sleep(time.Second)
					continue
				}

				c.processMessage(m.Value)
			}
		}
	}()
}

func (c *FileProcessingConsumer) processMessage(data []byte) {
	var task model.FileProcessingTask
	if err := json.Unmarshal(data, &task); err != nil {
		utils.ErrorLog("解析任务消息失败: %v", err)
		return
	}

	utils.FileOperationLog("kafka_consume", task.FileMd5, "fileName="+task.FileName)

	// 1. 下载文件
	reader, err := c.downloadFile(task.FilePath)
	if err != nil {
		utils.ErrorLog("下载文件失败 fileMd5=%s: %v", task.FileMd5, err)
		return
	}
	defer reader.Close()

	// 2. 解析文档
	if err := c.parseService.ParseFromReader(task.FileMd5, reader, task.UserID, task.OrgTag, task.IsPublic); err != nil {
		utils.ErrorLog("解析文档失败 fileMd5=%s: %v", task.FileMd5, err)
		return
	}

	// 3. 向量化并索引到ES
	if err := c.vectorizationSvc.Vectorize(task.FileMd5, task.UserID, task.OrgTag, task.IsPublic); err != nil {
		utils.ErrorLog("向量化失败 fileMd5=%s: %v", task.FileMd5, err)
		return
	}

	utils.FileOperationLog("kafka_done", task.FileMd5, "处理完成")
}

func (c *FileProcessingConsumer) downloadFile(filePath string) (io.ReadCloser, error) {
	// 支持本地路径和HTTP URL
	if strings.HasPrefix(filePath, "http://") || strings.HasPrefix(filePath, "https://") {
		client := &http.Client{
			Timeout: 3 * time.Minute,
			Transport: &http.Transport{
				ResponseHeaderTimeout: 30 * time.Second,
			},
		}
		resp, err := client.Get(filePath)
		if err != nil {
			return nil, err
		}
		if resp.StatusCode != http.StatusOK {
			resp.Body.Close()
			return nil, os.ErrNotExist
		}
		return resp.Body, nil
	}

	// 本地文件
	file, err := os.Open(filePath)
	if err != nil {
		return nil, err
	}
	return file, nil
}

// Close 关闭消费者
func (c *FileProcessingConsumer) Close() error {
	return c.reader.Close()
}
