package controller

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"strings"
	"sync"

	"goDemo/internal/service"
	"goDemo/internal/utils"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
)

// ChatController 聊天控制器
type ChatController struct {
	chatHandler *service.ChatHandler
	upgrader    websocket.Upgrader
	sessions    sync.Map // userID -> *websocket.Conn
	stopToken   string
}

func NewChatController(chatHandler *service.ChatHandler) *ChatController {
	// 生成内部命令Token
	tokenBytes := make([]byte, 16)
	rand.Read(tokenBytes)
	stopToken := "WSS_STOP_CMD_" + hex.EncodeToString(tokenBytes)

	return &ChatController{
		chatHandler: chatHandler,
		upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool { return true },
		},
		stopToken: stopToken,
	}
}

// GetWebSocketToken 获取WebSocket命令Token
func (ctrl *ChatController) GetWebSocketToken(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"token": ctrl.stopToken})
}

// HandleWebSocket 处理WebSocket连接
func (ctrl *ChatController) HandleWebSocket(c *gin.Context) {
	// 从URL路径获取Token
	token := c.Param("token")
	if token == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "缺少认证Token"})
		return
	}

	// 验证JWT
	claims, err := utils.ParseToken(token)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "无效的Token"})
		return
	}

	userID := claims.UserID

	// 升级为WebSocket
	conn, err := ctrl.upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		return
	}
	defer conn.Close()

	// 保存会话
	ctrl.sessions.Store(userID, conn)
	defer ctrl.sessions.Delete(userID)

	// 读取消息循环
	for {
		_, message, err := conn.ReadMessage()
		if err != nil {
			break
		}

		msgStr := string(message)

		// 检查是否是停止命令
		if strings.Contains(msgStr, ctrl.stopToken) {
			_ = ctrl.chatHandler.StopResponse(userID)
			continue
		}

		// 处理聊天消息
		go func() {
			err := ctrl.chatHandler.ProcessMessage(userID, msgStr, func(chunk string) {
				// 通过WebSocket发送每个分块
				if conn, ok := ctrl.sessions.Load(userID); ok {
					wsConn := conn.(*websocket.Conn)
					msg, _ := json.Marshal(map[string]string{
						"type":    "chunk",
						"content": chunk,
					})
					_ = wsConn.WriteMessage(websocket.TextMessage, msg)
				}
			})
			if err != nil {
				// 发送错误消息
				if conn, ok := ctrl.sessions.Load(userID); ok {
					wsConn := conn.(*websocket.Conn)
					msg, _ := json.Marshal(map[string]string{
						"type":    "error",
						"content": err.Error(),
					})
					_ = wsConn.WriteMessage(websocket.TextMessage, msg)
				}
			}
			// 发送完成消息
			if conn, ok := ctrl.sessions.Load(userID); ok {
				wsConn := conn.(*websocket.Conn)
				msg, _ := json.Marshal(map[string]string{
					"type": "complete",
				})
				_ = wsConn.WriteMessage(websocket.TextMessage, msg)
			}
		}()
	}
}
