CREATE TABLE IF NOT EXISTS game_results (
    game_id     VARCHAR(36)  NOT NULL,
    nickname    VARCHAR(50)  NOT NULL,
    score       INT          NOT NULL,
    num_players INT          NOT NULL,
    played_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (game_id, nickname)
);
