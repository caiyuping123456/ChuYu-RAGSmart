package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.utils.PasswordUtil;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Optional;

import static org.hamcrest.Matchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * UserService 的测试类
 */
@SpringBootTest
class UserServiceTest {
    // 模拟 UserRepository 实例
    @Mock
    private UserRepository userRepository;

    // 注入模拟的 UserService 实例
    @Resource
    private UserService userService;

    /**
     * 在每个测试方法执行前初始化模拟对象
     */
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * 测试用户注册成功的情况
     */
    @Test
    void testRegisterUser_Success() {
        // 假设用户名 "testuser" 在数据库中不存在
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        // 调用 userService 的 registerUser 方法进行用户注册
        userService.registerUser("testuser", "password123");

        // 创建 ArgumentCaptor 来捕获 save 方法的参数
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // 验证 userRepository.save 被调用了一次，并捕获参数
        verify(userRepository, times(1)).save(userCaptor.capture());

        // 获取捕获的 User 对象并进行断言
        User savedUser = userCaptor.getValue();
        assertNotNull(savedUser);
        assertEquals("testuser", savedUser.getUsername());
    }

    /**
     * 测试用户注册时用户名已存在的情况
     */
    @Test
    void testRegisterUser_UsernameExists() {
        // 假设用户名 "testuser" 在数据库中已存在
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(new User()));

        // 断言在注册已存在的用户名时抛出 CustomException 异常
        CustomException exception = assertThrows(CustomException.class, () -> userService.registerUser("testuser", "password123"));
        assertEquals("Username already exists", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    /**
     * 测试用户认证成功的情况
     */
   @Test
void testAuthenticateUser_Success() {
    // 使用 PasswordUtil 生成加密密码
    String rawPassword = "password123";
    String encodedPassword = PasswordUtil.encode(rawPassword);

    // 创建一个带有加密密码的用户对象，并确保设置了用户名
    User user = new User();
    user.setUsername("testuser"); // 确保设置了用户名
    user.setPassword(encodedPassword);

    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

    try {
        // 调用 userService 的 authenticateUser 方法进行用户认证
        String username = userService.authenticateUser("testuser", rawPassword);

        // 打印返回的用户名（仅用于调试）
        System.out.println("Returned username: " + username);

        // 断言返回的用户名是否正确
        assertEquals("testuser", username);
    } catch (CustomException e) {
        // 捕获并打印异常信息
        System.out.println("Exception message: " + e.getMessage());
        throw e;
    }

    // 打印实际的加密密码（仅用于调试）
    System.out.println("Actual encrypted password: " + user.getPassword());
}





    /**
     * 测试用户认证失败的情况
     */
    @Test
    void testAuthenticateUser_InvalidCredentials() {
        // 假设用户名 "testuser" 在数据库中不存在
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        // 断言在使用错误密码认证时抛出 CustomException 异常
        CustomException exception = assertThrows(CustomException.class, () -> userService.authenticateUser("testuser", "wrongpassword"));
        assertEquals("Invalid username or password", exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Autowired
    private JavaMailSender mailSender;
    /**
     * 测试邮箱发送
     * @throws MessagingException
     */
    @Test
    void testPostMessageMail() throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom("761962102@qq.com");
        helper.setTo("761962102@qq.com");
        helper.setSubject("ChuYu智能系统 - 注册验证码");

        // 漂亮的 HTML 内容
        String htmlContent = "<html>" +
                "<body>" +
                "<h2 style='color:#333;'>欢迎注册 ChuYu 智能系统</h2>" +
                "<p>您的验证码为：</p>" +
                "<h1 style='color:#1890ff; letter-spacing: 5px; text-align:center;'>" + "123456" + "</h1>" +
                "<p>该验证码 <b>5 分钟内有效</b>，请勿泄露。</p>" +
                "<hr style='border: 1px solid #eee;'>" +
                "<p style='color:gray; font-size: 12px;'>如非本人操作，请忽略此邮件。</p>" +
                "</body>" +
                "</html>";

        helper.setText(htmlContent, true);
        mailSender.send(mimeMessage);
    }
}
