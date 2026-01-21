package utils

import (
	"log"
	"os"
	"time"
)

var (
	infoLog  = log.New(os.Stdout, "[INFO] ", log.LstdFlags|log.Lshortfile)
	warnLog  = log.New(os.Stdout, "[WARN] ", log.LstdFlags|log.Lshortfile)
	errorLog = log.New(os.Stderr, "[ERROR] ", log.LstdFlags|log.Lshortfile)
)

// BusinessLog 业务日志
func BusinessLog(format string, args ...interface{}) {
	infoLog.Printf("[BUSINESS] "+format, args...)
}

// PerformanceLog 性能日志
func PerformanceLog(operation string, duration time.Duration, extra ...string) {
	infoLog.Printf("[PERF] %s took %v %v", operation, duration, extra)
}

// FileOperationLog 文件操作日志
func FileOperationLog(operation, fileMd5 string, extra ...string) {
	infoLog.Printf("[FILE] %s fileMd5=%s %v", operation, fileMd5, extra)
}

// ChatLog 聊天日志
func ChatLog(userID string, message string, extra ...string) {
	infoLog.Printf("[CHAT] userID=%s msg=%s %v", userID, message, extra)
}

// ErrorLog 错误日志
func ErrorLog(format string, args ...interface{}) {
	errorLog.Printf(format, args...)
}

// WarnLog 警告日志
func WarnLog(format string, args ...interface{}) {
	warnLog.Printf(format, args...)
}
