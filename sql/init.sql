-- CREATE DATABASE gamebot;
-- \c gamebot;

create table teams(
    team_id BIGINT NOT NULL,
    name TEXT NOT NULL,
    PRIMARY KEY (team_id)
);

CREATE TABLE stared
(
    user_id BIGINT NOT NULL,
    game_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, game_id)
);

CREATE TABLE games
(
    game_id BIGINT NOT NULL,
    home_id BIGINT NOT NULL,
    away_id BIGINT NOT NULL,
    game_date BIGINT NOT NULL,
    PRIMARY KEY (game_id)
);

ALTER TABLE games
    ADD FOREIGN KEY (home_id) REFERENCES teams (team_id);
ALTER TABLE games
    ADD FOREIGN KEY (away_id) REFERENCES teams (team_id);



INSERT INTO teams
    (team_id, name)
VALUES
    (1, 'NJD'),
    (2, 'NYI'),
    (3, 'NYR'),
    (4, 'PHI'),
    (6, 'BOS'),
    (5, 'PIT'),
    (7, 'BUF'),
    (8, 'MTL'),
    (9, 'OTT'),
    (10, 'TOR'),
    (12, 'CAR'),
    (13, 'FLA'),
    (14, 'TBL'),
    (15, 'WSH'),
    (16, 'CHI'),
    (17, 'DET'),
    (18, 'NSH'),
    (19, 'STL'),
    (20, 'CGY'),
    (21, 'COL'),
    (22, 'EDM'),
    (23, 'VAN'),
    (24, 'ANA'),
    (25, 'DAL'),
    (26, 'LAK'),
    (28, 'SJS'),
    (29, 'CBJ'),
    (30, 'MIN'),
    (52, 'WPG'),
    (53, 'ARI'),
    (54, 'VGK'),
    (55, 'SEA');
COMMIT;