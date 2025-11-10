package com.codingplatform.services.email

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EmailConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val fromEmail: String,
    val fromName: String,
    val useStartTls: Boolean = true
)

class EmailService(
    private val config: EmailConfig
) {
    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", config.useStartTls.toString())
            put("mail.smtp.starttls.required", config.useStartTls.toString())
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
            put("mail.smtp.connectiontimeout", "5000")
            put("mail.smtp.timeout", "5000")
            put("mail.smtp.writetimeout", "5000")
        }
        Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(config.username, config.password)
            }
        )
    }

    suspend fun sendVerificationCode(targetEmail: String, code: String) {
        val subject = "Coding Platform 이메일 인증 코드"
        val body = buildString {
            appendLine("안녕하세요, Coding Platform 입니다.")
            appendLine()
            appendLine("아래 인증 코드를 입력하여 회원가입을 완료하세요.")
            appendLine()
            appendLine("인증 코드: $code")
            appendLine("유효 시간: 10분")
            appendLine()
            appendLine("감사합니다.")
        }

        withContext(Dispatchers.IO) {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(config.fromEmail, config.fromName))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(targetEmail, false))
                setSubject(subject, Charsets.UTF_8.name())
                setText(body, Charsets.UTF_8.name())
            }
            Transport.send(message)
        }
    }
}
