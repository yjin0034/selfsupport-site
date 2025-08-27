SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- 1) 사용자 계정
CREATE TABLE IF NOT EXISTS user_account (
                                            user_id        VARCHAR(50)  NOT NULL,         -- PK (유저 아이디)
                                            user_password  VARCHAR(255) NOT NULL,
                                            email          VARCHAR(100),
                                            nickname       VARCHAR(100),
                                            memo           VARCHAR(255),
                                            created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            created_by     VARCHAR(100),
                                            modified_at    DATETIME              DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
                                            modified_by    VARCHAR(100),
                                            PRIMARY KEY (user_id),
                                            UNIQUE KEY uk_user_account_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 게시글
CREATE TABLE IF NOT EXISTS article (
                                       id               BIGINT      NOT NULL AUTO_INCREMENT,
                                       user_account_id  VARCHAR(50) NOT NULL,        -- FK → user_account.user_id
                                       title            VARCHAR(255) NOT NULL,
                                       content          TEXT,
                                       hashtag          VARCHAR(255),
                                       created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       created_by       VARCHAR(100),
                                       modified_at      DATETIME             DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
                                       modified_by      VARCHAR(100),
                                       PRIMARY KEY (id),
                                       KEY idx_article_user_account_id (user_account_id),
                                       CONSTRAINT fk_article_user_account
                                           FOREIGN KEY (user_account_id) REFERENCES user_account(user_id)
                                               ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 댓글
CREATE TABLE IF NOT EXISTS article_comment (
                                               id               BIGINT      NOT NULL AUTO_INCREMENT,
                                               article_id       BIGINT      NOT NULL,        -- FK → article.id
                                               user_account_id  VARCHAR(50) NOT NULL,        -- FK → user_account.user_id
                                               content          TEXT        NOT NULL,
                                               created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               created_by       VARCHAR(100),
                                               modified_at      DATETIME             DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
                                               modified_by      VARCHAR(100),
                                               PRIMARY KEY (id),
                                               KEY idx_comment_article_id (article_id),
                                               KEY idx_comment_user_account_id (user_account_id),
                                               CONSTRAINT fk_comment_article
                                                   FOREIGN KEY (article_id) REFERENCES article(id)
                                                       ON DELETE CASCADE ON UPDATE CASCADE,
                                               CONSTRAINT fk_comment_user_account
                                                   FOREIGN KEY (user_account_id) REFERENCES user_account(user_id)
                                                       ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
