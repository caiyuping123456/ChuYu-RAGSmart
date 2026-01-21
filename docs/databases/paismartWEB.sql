/*
 Navicat Premium Data Transfer

 Source Server         : 120.48.177.166
 Source Server Type    : MySQL
 Source Server Version : 80039 (8.0.39)
 Source Host           : 120.48.177.166:3306
 Source Schema         : paismart

 Target Server Type    : MySQL
 Target Server Version : 80039 (8.0.39)
 File Encoding         : 65001

 Date: 10/04/2026 15:52:34
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for chunk_info
-- ----------------------------
DROP TABLE IF EXISTS `chunk_info`;
CREATE TABLE `chunk_info`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分块记录唯一标识',
  `file_md5` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `chunk_index` int NOT NULL COMMENT '分块序号',
  `chunk_md5` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `storage_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分块在存储系统中的路径',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文件分块信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of chunk_info
-- ----------------------------

-- ----------------------------
-- Table structure for conversations
-- ----------------------------
DROP TABLE IF EXISTS `conversations`;
CREATE TABLE `conversations`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `question` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `timestamp` datetime(6) NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_timestamp`(`timestamp` ASC) USING BTREE,
  CONSTRAINT `FKpltqvfcbkql9svdqwh0hw4g1d` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of conversations
-- ----------------------------

-- ----------------------------
-- Table structure for document_vectors
-- ----------------------------
DROP TABLE IF EXISTS `document_vectors`;
CREATE TABLE `document_vectors`  (
  `vector_id` bigint NOT NULL AUTO_INCREMENT COMMENT '向量记录唯一标识',
  `file_md5` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '关联的文件MD5值',
  `chunk_id` int NOT NULL COMMENT '文本分块序号',
  `text_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `model_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '向量模型版本',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '上传用户ID',
  `org_tag` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件所属组织标签',
  `is_public` tinyint(1) NOT NULL DEFAULT 0 COMMENT '文件是否公开',
  PRIMARY KEY (`vector_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1098 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文档向量存储表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of document_vectors
-- ----------------------------

-- ----------------------------
-- Table structure for file_upload
-- ----------------------------
DROP TABLE IF EXISTS `file_upload`;
CREATE TABLE `file_upload`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_md5` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件 MD5',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件名称',
  `total_size` bigint NOT NULL COMMENT '文件大小',
  `status` int NOT NULL,
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户 ID',
  `org_tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `is_public` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否公开',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `merged_at` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '合并时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_md5_user`(`file_md5` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_org_tag`(`org_tag` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文件上传记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of file_upload
-- ----------------------------

-- ----------------------------
-- Table structure for organization_tags
-- ----------------------------
DROP TABLE IF EXISTS `organization_tags`;
CREATE TABLE `organization_tags`  (
  `tag_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '标签唯一标识',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '描述',
  `parent_tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '父标签ID',
  `created_by` bigint NOT NULL COMMENT '创建者ID',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`tag_id`) USING BTREE,
  INDEX `parent_tag`(`parent_tag` ASC) USING BTREE,
  INDEX `created_by`(`created_by` ASC) USING BTREE,
  CONSTRAINT `organization_tags_ibfk_1` FOREIGN KEY (`parent_tag`) REFERENCES `organization_tags` (`tag_id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `organization_tags_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '组织标签表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of organization_tags
-- ----------------------------
INSERT INTO `organization_tags` VALUES ('DEFAULT', '默认组织', '系统默认组织标签，自动分配给所有新用户', NULL, 1, '2026-01-07 13:24:23', '2026-01-07 13:24:23');
INSERT INTO `organization_tags` VALUES ('PRIVATE_caiyuping', 'caiyuping的私人空间', '用户的私人组织标签，仅用户本人可访问', NULL, 2, '2026-01-07 13:24:24', '2026-01-07 13:24:24');
INSERT INTO `organization_tags` VALUES ('admin', '管理员组织', '管理员专用组织标签，具有管理权限', NULL, 1, '2026-01-07 08:25:27', '2026-01-07 08:25:27');
INSERT INTO `organization_tags` VALUES ('default', '默认组织', '系统默认组织标签，自动分配给所有新用户', NULL, 1, '2026-01-07 08:25:27', '2026-01-07 08:25:27');

-- ----------------------------
-- Table structure for test_entity
-- ----------------------------
DROP TABLE IF EXISTS `test_entity`;
CREATE TABLE `test_entity`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of test_entity
-- ----------------------------

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户唯一标识',
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名，唯一',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '加密后的密码',
  `role` enum('USER','ADMIN') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'USER' COMMENT '用户角色',
  `org_tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户所属组织标签，多个用逗号分隔',
  `primary_org` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `UKr43af9ap4edm43mmtq01oddj6`(`username` ASC) USING BTREE,
  INDEX `idx_username`(`username` ASC) USING BTREE COMMENT '用户名索引'
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` VALUES (1, 'admin', '$2a$10$iYWGVGE1b6V7c3OJyZ.xCeQ7kJzyeGoIC8UMmEO8/lxewye0.1vBC', 'ADMIN', 'default,admin', 'default', '2026-01-07 08:25:27', '2026-01-07 08:25:27');
INSERT INTO `users` VALUES (2, 'caiyuping', '$2a$10$fxeUpGlunhyS0AlUuLXD9ubEkk2UZGjZK6T464rnVg3FZgbHvpDf6', 'USER', 'PRIVATE_caiyuping,DEFAULT', 'PRIVATE_caiyuping', '2026-01-07 13:24:23', '2026-03-25 04:49:22');

SET FOREIGN_KEY_CHECKS = 1;
