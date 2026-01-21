package exception

import "fmt"

// CustomException 自定义异常
type CustomException struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

func (e *CustomException) Error() string {
	return fmt.Sprintf("[%d] %s", e.Code, e.Message)
}

func New(code int, message string) *CustomException {
	return &CustomException{Code: code, Message: message}
}

func NewBadRequest(message string) *CustomException {
	return &CustomException{Code: 400, Message: message}
}

func NewUnauthorized(message string) *CustomException {
	return &CustomException{Code: 401, Message: message}
}

func NewForbidden(message string) *CustomException {
	return &CustomException{Code: 403, Message: message}
}

func NewNotFound(message string) *CustomException {
	return &CustomException{Code: 404, Message: message}
}

func NewInternal(message string) *CustomException {
	return &CustomException{Code: 500, Message: message}
}
