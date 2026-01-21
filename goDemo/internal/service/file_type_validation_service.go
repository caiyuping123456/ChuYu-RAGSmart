package service

// FileTypeValidationService 文件类型验证服务
type FileTypeValidationService struct{}

func NewFileTypeValidationService() *FileTypeValidationService {
	return &FileTypeValidationService{}
}

// GetSupportedTypes 获取支持的文件类型
func (s *FileTypeValidationService) GetSupportedTypes() map[string][]string {
	return map[string][]string{
		"文档": {".pdf", ".doc", ".docx", ".txt", ".md", ".rtf"},
		"表格": {".xls", ".xlsx", ".csv"},
		"演示": {".ppt", ".pptx"},
		"数据": {".json", ".xml", ".yaml", ".yml"},
		"网页": {".html", ".htm"},
		"图片": {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"},
	}
}

// GetSupportedExtensions 获取所有支持的扩展名
func (s *FileTypeValidationService) GetSupportedExtensions() []string {
	types := s.GetSupportedTypes()
	var exts []string
	for _, v := range types {
		exts = append(exts, v...)
	}
	return exts
}

// IsSupported 检查文件类型是否支持
func (s *FileTypeValidationService) IsSupported(filename string) bool {
	exts := s.GetSupportedExtensions()
	for _, ext := range exts {
		if len(filename) >= len(ext) && filename[len(filename)-len(ext):] == ext {
			return true
		}
	}
	return false
}
