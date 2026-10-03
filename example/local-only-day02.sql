USE quephoto_dev;
START TRANSACTION;
INSERT INTO tag_group (id, name, sort_order, created_at) VALUES
                                                             (11, '题材', 10, UTC_TIMESTAMP(3)),
                                                             (12, '地点类型', 20, UTC_TIMESTAMP(3)),
                                                             (13, '暂未配置', 30, UTC_TIMESTAMP(3));
INSERT INTO tag (id, group_id, name, sort_order, created_at) VALUES
                                                                 (111, 11, '街拍', 10, UTC_TIMESTAMP(3)),
                                                                 (112, 11, '风景', 20, UTC_TIMESTAMP(3)),
                                                                 (121, 12, '城市', 10, UTC_TIMESTAMP(3));
INSERT INTO portfolio
(id, title, description, location, shot_at, shot_time_precision,
 status, created_at, updated_at) VALUES
                                     (1001, '雨夜', '本地样例', '上海', '2026-09-01 00:00:00', 'month',
                                      'draft', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                                     (1002, '未知日期', NULL, NULL, NULL, NULL,
                                      'draft', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                                     (1003, '未公开作品', NULL, '杭州', '2026-10-01 00:00:00', 'day',
                                      'draft', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO portfolio_image
(id, portfolio_id, image_type, object_key, sort_order, width, height, created_at)
VALUES
    (2001, 1001, 'work', 'photos/local-only/A.jpg', 20, 1200, 800, UTC_TIMESTAMP(3)),
    (2002, 1001, 'scene', 'photos/local-only/B.jpg', 0, NULL, NULL, UTC_TIMESTAMP(3)),
    (2003, 1002, 'work', 'photos/local-only/a.jpg', 10, NULL, NULL, UTC_TIMESTAMP(3));
UPDATE portfolio SET cover_image_id = 2001, status = 'published',
                     updated_at = UTC_TIMESTAMP(3) WHERE id = 1001;
UPDATE portfolio SET cover_image_id = 2003, status = 'published',
                     updated_at = UTC_TIMESTAMP(3) WHERE id = 1002;
INSERT INTO portfolio_tag (portfolio_id, tag_group_id, tag_id) VALUES
                                                                   (1001, 11, 111), (1001, 12, 121), (1002, 11, 112), (1003, 11, 111);
COMMIT;