package com.realtimechat;

import com.realtimechat.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Application entry point.
 *
 * <p>This is a modular monolith: the Spring context is a single process, but the code is split into
 * feature modules (auth, users, profiles, conversations, messaging, presence, notifications,
 * moderation). Each module owns its entities, repositories and services; cross-module access goes
 * through the callee's public service interface. See {@code docs/architecture.md}.
 *
 * <p>{@code @EnableAsync} is required for notification fan-out, {@code @EnableScheduling} for the
 * retention jobs (session/token/notification pruning).
 *
 * <p>Configuration lives in {@link AppProperties}; the {@code prod} profile is validated at startup
 * by {@code ProductionReadinessValidator}, which fails the context rather than degrading silently.
 *
 * <p>One operator utility lives here rather than in a separate artefact: {@code --hash-password}
 * prints an Argon2id PHC string for a password and exits, without starting the web context. The
 * development seed needs such a hash (it must never contain a plaintext or reversible password), and
 * an image that cannot generate its own hashes forces operators to install a JVM and a build tool to
 * do it — which is exactly how plaintext passwords end up in a seed file.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
@EnableScheduling
public class RealtimeChatApplication {

    public static void main(String[] args) {
        String passwordToHash = passwordArgument(args);
        if (passwordToHash != null) {
            hashPasswordAndExit(passwordToHash);
            return;
        }
        SpringApplication.run(RealtimeChatApplication.class, args);
    }

    /**
     * Reads {@code --hash-password=<value>} or {@code --hash-password <value>} from the arguments.
     *
     * @return the password to hash, or {@code null} when the flag is absent (the normal case)
     */
    private static String passwordArgument(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (argument.startsWith("--hash-password=")) {
                return argument.substring("--hash-password=".length());
            }
            if ("--hash-password".equals(argument) && index + 1 < args.length) {
                return args[index + 1];
            }
        }
        return null;
    }

    /**
     * Prints an Argon2id hash using the same encoder the application verifies logins with, so a hash
     * generated here is guaranteed to be accepted at sign-in.
     *
     * <p>The password is read from the command line (or an environment variable expanded by the
     * shell) and never logged: only the hash reaches standard output.
     */
    private static void hashPasswordAndExit(String password) {
        if (password.isBlank()) {
            System.err.println("Refusing to hash an empty password.");
            System.exit(2);
        }
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(password);
        System.out.println(hash);
        System.exit(0);
    }
}
