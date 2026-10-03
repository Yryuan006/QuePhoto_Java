-- ============================================================================
-- Day 02：QuePhoto 的第一版数据库结构（MySQL 8.4）
-- ============================================================================
-- 本文件的职责：创建五张业务表及它们的主键、索引、外键和检查约束。
-- 它不创建数据库账号，不设置密码，也不插入示例作品。
-- 数据写入以后由 INSERT / UPDATE 完成；本文件的 CREATE / ALTER 属于结构定义。
--
-- 文件名 V1__init_schema.sql 的含义：
--   V1          ：第 1 个版本的数据库迁移。
--   __          ：两个下划线，用来分隔版本号和描述。
--   init_schema ：描述，意为“初始化表结构”。
--   .sql        ：文件内容是 SQL 语句。
-- 启动应用时，Flyway 根据配置找到本文件，在应用连接的数据库中执行它。
-- 成功后，Flyway 在自己维护的 flyway_schema_history 表中记录版本与校验信息。
-- 这张历史表由 Flyway 创建，因此不在下面的五张业务表定义中。
-- 已成功执行的迁移再次启动时会被校验，而不会重新执行建表。
-- 迁移一旦执行并需要保留其历史，后续修改结构应新增 V2__...sql；
-- 修改 V1 的注释也可能改变校验和，因此应在首次执行前完善这份学习注释。
--
-- 五表关系先读成下面几句话：
--   portfolio       ：一条记录代表一个作品集，以下也简称“作品”。
--   portfolio_image ：一条记录代表一张登记到作品中的图片。
--   tag_group       ：一个标签分类，例如“题材”“地点类型”。
--   tag             ：分类中的选项，例如“街拍”“风景”“城市”。
--   portfolio_tag   ：记录“哪个作品，在哪个组，选择了哪个标签”。
-- 一个作品可以暂时没有图片、封面或标签；一个标签组也可以暂时没有标签。
--
-- 阅读 SQL 的基本约定：
--   1. 本文件所有以“两个减号加空格”开头的行都是注释，不参与执行。
--      注释里的 SQL 示例也不会执行，可用于对照理解。
--   2. CREATE TABLE 表名 (...)：创建表；括号内列出字段和约束。
--      字段定义通常是“字段名 数据类型 其他要求”，定义之间用逗号分隔。
--      括号中的最后一项后面没有逗号；一个完整 SQL 语句以分号 ; 结束。
--   3. 本文件用单引号包住字符串，例如 'draft'；数字通常不用引号。
--      空字符串 ''、数字 0、字符串 'NULL'，都不是 SQL 的 NULL。
--   4. NULL 表示“没有提供这个值/未知”；NOT NULL 表示这个字段不能为 NULL。
--      NOT NULL 本身不禁止空字符串，标题是否有效还要看后面的 CHECK。
--      判断是否为空写 IS NULL / IS NOT NULL，不写 = NULL / != NULL。
--   5. DEFAULT 是省略字段时采用的默认值，不会把任意非法输入变成默认值。
--      字段允许 NULL 时，显式写入 NULL 与省略字段也是不同的情况。
--   6. PRIMARY KEY 是主键：每张表只有一个主键约束，但可包含多个字段。
--      主键列不能为 NULL，整组主键值不能重复，并会建立相应索引。
--   7. KEY 是普通索引（这里等同于 INDEX）：帮助查找，可以有重复值。
--      UNIQUE KEY 是唯一索引：除帮助查找外，还限制索引键值不能重复。
--      本文件所有唯一索引的组成字段都是 NOT NULL。
--      一般情况下 MySQL 的可空唯一索引允许多个 NULL，不可据此推断 NULL 唯一。
--   8. FOREIGN KEY ... REFERENCES ... 是外键：关联值必须满足父表引用关系。
--      外键不把另一张表的数据复制过来；想一起查询数据，需要显式写 JOIN 等 SQL。
--   9. CHECK (...) 检查新写入或修改后的行是否符合条件。
--      AND 表示条件同时成立，OR 表示至少一项成立，IN (...) 表示属于某组值。
--      MySQL CHECK 拒绝 FALSE，但 TRUE 和 UNKNOWN 都可能通过。
--      因此与 NULL 有关的分支要显式判断，下面的时间和宽高约束会示范。
--  10. CONSTRAINT 后面是约束名，便于报错定位；名字本身不决定约束行为。
--      ix_ / uq_ / fk_ / ck_ 是本项目的普通索引/唯一索引/外键/CHECK 命名约定。
--  11. 联合索引有字段顺序，常从最左侧连续字段开始用于定位数据。
--      索引是否被使用由具体查询和优化器决定；有索引不等于所有查询都会变快。
--      查询结果要有确定顺序，仍要写 ORDER BY，不能依赖索引带来的偶然顺序。
--      索引也占存储空间，写入时还需要维护，所以不必给每列都单独加索引。
--
-- 建表顺序：作品 -> 图片 -> 补作品封面外键 -> 标签组 -> 标签 -> 作品标签关联。
-- 原因是外键要引用已经存在的表；作品与图片互相引用，所以封面外键稍后再加。
-- ============================================================================

-- ============================================================================
-- 1. portfolio：作品表
-- ============================================================================
CREATE TABLE portfolio (
    -- BIGINT 是 8 字节整数，适合保存记录编号；这里没有声明 UNSIGNED。
    -- NOT NULL 禁止空值；AUTO_INCREMENT 让正常省略 id 的 INSERT 自动分配编号。
    -- 自增负责生成编号，下面的 PRIMARY KEY 才负责保证唯一性。
    -- 自增值可能因删除、失败插入等出现间隔，不能用“最大 id”当作记录总数。
    id BIGINT NOT NULL AUTO_INCREMENT,

    -- VARCHAR(100) 表示变长字符串，最大长度为 100 个字符，不是 100 个字节。
    -- NOT NULL 只保证有值；下面的 ck_portfolio_title 进一步拒绝空标题。
    -- 标题没有 UNIQUE 约束，所以不同作品可以使用相同标题。
    title VARCHAR(100) NOT NULL,

    -- TEXT 保存较长的文字；NULL 允许作品暂时没有描述。
    -- TEXT 自身有存储上限；本项目另外用 CHECK 把业务上限设为 10000 个字符。
    description TEXT NULL,

    -- 拍摄地点的描述，例如“上海”“杭州西湖”；未知时保存 NULL。
    -- 这里只存一段文本，当前设计没有独立的地点表或地点外键。
    location VARCHAR(200) NULL,

    -- DATETIME 保存年月日时分秒；这里未指定小数秒精度，精确到秒。
    -- DATETIME 本身不附带时区。本项目把 shot_at 理解为拍摄当地的墙上时间。
    -- 只知道月份时也需要存完整日期，再由下一个字段说明哪些部分确实已知。
    shot_at DATETIME NULL,

    -- 拍摄时间的“已知程度”：year / month / day / hour / minute。
    -- 例如只知道 2026 年 9 月：shot_at 用 2026-09-01 00:00:00 占位，精度为 month。
    -- 其中“1 日零点”不代表真实拍摄时刻；展示时按精度显示“2026 年 9 月”。
    -- 合法取值由后面的 CHECK 限制；占位日期是否规范需要由 Java Service 校验。
    -- COLLATE 指定本列的字符比较规则，表末还会解释同名的默认规则。
    shot_time_precision VARCHAR(10) COLLATE utf8mb4_0900_bin NULL,

    -- 作品状态：draft 为草稿，published 为已发布。
    -- INSERT 省略 status 时默认是 draft；这个默认值不会自动把作品发布出去。
    -- 公开查询还必须主动带上 WHERE status = 'published'，字段本身不负责权限隔离。
    status VARCHAR(10) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT 'draft',

    -- 保存封面图片的 id，而不是图片路径或图片文件内容。
    -- 草稿可以没有封面，所以允许 NULL；外键要等图片表创建完后再添加。
    -- 这里没有 UNIQUE，单靠数据库结构不能阻止多个作品引用同一个封面 id。
    cover_image_id BIGINT NULL,

    -- 预留分享图在 OSS 对象存储中的 Key，例如 shares/example.jpg。
    -- Key 是存储对象的标识，不是完整访问 URL，也不是 portfolio_image 的外键。
    -- 本周 P0 不开放独立分享图写入，接口中的分享图 URL 回退到封面 URL。
    share_image_key VARCHAR(500) COLLATE utf8mb4_0900_bin NULL,

    -- DATETIME(3) 的 3 表示保留三位小数秒，即毫秒，如 ...12:30:45.123。
    -- created_at 是记录创建时间；本项目约定审计时间按 UTC 保存。
    -- 这里没有 DEFAULT，写入代码必须显式提供，例如 UTC_TIMESTAMP(3)。
    created_at DATETIME(3) NOT NULL,

    -- updated_at 是作品最后修改时间，同样约定为 UTC。
    -- 这里也没有 ON UPDATE 自动更新时间；每次作品写操作由应用显式维护。
    -- “有这个字段”并不等于数据库会自动追踪修改。
    updated_at DATETIME(3) NOT NULL,

    -- 作品的主键：两条记录不能拥有同一个 id。
    PRIMARY KEY (id),

    -- 普通联合索引，按 status、shot_at、id 的列顺序组织索引键。
    -- 面向公开列表的状态过滤和拍摄时间排序；同一时间再用 id 稳定排序。
    -- 例如先筛 published，再考虑 shot_at 和 id，符合索引的左侧字段顺序。
    -- 教程查询还有“日期未知排最后”的表达式排序，不能保证整个排序都由本索引完成。
    KEY ix_portfolio_public (status, shot_at, id),

    -- 为封面引用列建立普通索引，支持相关查找以及稍后添加的外键约束。
    -- 这是普通 KEY，不是 UNIQUE KEY，不限制 cover_image_id 的重复。
    KEY ix_portfolio_cover (cover_image_id),

    -- TRIM(title) 默认去掉两端的普通空格，CHAR_LENGTH 计算字符数量。
    -- 结果必须大于 0，所以 '' 和只由普通空格组成的标题会被拒绝。
    -- TRIM 这里只参与检查，不会把去空格后的结果自动保存回 title。
    -- 它也不是“清理所有 Unicode 空白字符”；应用仍需按业务要求处理输入。
    CONSTRAINT ck_portfolio_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),

    -- 描述可以不填；有值时，字符数不得超过 10000。
    -- CHAR_LENGTH 按字符计数，而 LENGTH 对字符串计的是字节数。
    CONSTRAINT ck_portfolio_description CHECK (
        description IS NULL OR CHAR_LENGTH(description) <= 10000
    ),

    -- 只允许这两个状态值；例如 'deleted' 和大写 'PUBLISHED' 都不符合本定义。
    -- 检查取值不等于检查发布条件：封面是否合格仍由 Service 判断。
    CONSTRAINT ck_portfolio_status CHECK (status IN ('draft', 'published')),

    -- 拍摄时间与精度只能两者都空，或者两者都有值且精度合法。
    -- 合法：NULL + NULL；完整日期 + 'month'。
    -- 非法：有日期但精度 NULL；日期 NULL 但精度 'day'；精度写成 'second'。
    -- 有值分支必须明确写 IS NOT NULL，避免 NULL 参与比较后得到 UNKNOWN 而被放行。
    -- 这里没有要求 month 必须是每月 1 日零点，日期规范化仍是 Service 的职责。
    CONSTRAINT ck_portfolio_shot_pair CHECK (
        (shot_at IS NULL AND shot_time_precision IS NULL)
        OR
        (shot_at IS NOT NULL AND shot_time_precision IS NOT NULL
            AND shot_time_precision IN ('year', 'month', 'day', 'hour', 'minute'))
    )
-- ENGINE=InnoDB：选择支持事务、行级锁和外键的 MySQL 存储引擎。
-- DEFAULT CHARSET=utf8mb4：表的默认字符集，支持中文及 emoji 等 Unicode 字符。
-- COLLATE=utf8mb4_0900_bin：默认字符比较规则，区分大小写，也区分尾部空格。
-- 例如 A.jpg 与 a.jpg、a.jpg 与末尾多一个空格的 a.jpg，在比较时不同。
-- 未单独指定字符集/比较规则的字符列继承表的默认值；本文件五张表保持一致。
-- 列上再次写相同 COLLATE 是把关键字段的比较要求明确标出，并不改变上述规则。
-- utf8mb4_0900_bin 的尾部空格规则见文末官方资料 [3]。
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- ============================================================================
-- 2. portfolio_image：作品图片表
-- ============================================================================
CREATE TABLE portfolio_image (
    -- 图片记录自己的编号，不同于它所属作品的编号。
    id BIGINT NOT NULL AUTO_INCREMENT,

    -- 所属作品的 id；下面的外键要求这个作品确实存在。
    -- 该列可以重复，因此多张图片可以指向同一个作品，实现“作品一对多图片”。
    -- NOT NULL 加上外键，保证每张图片登记到一个存在的作品。
    portfolio_id BIGINT NOT NULL,

    -- 图片用途：work 为作品图，scene 为场景图；合法值由下方 CHECK 限制。
    -- 图片用途用字符串保存，并没有单独建立图片类型表。
    image_type VARCHAR(10) COLLATE utf8mb4_0900_bin NOT NULL,

    -- OSS 对象 Key，例如 photos/2026/10/example.jpg；这里只存标识，不存图片二进制。
    -- 后续可用配置中的访问域名与 Key 生成 URL；生成 URL 不等于对象已经存在。
    -- NOT NULL 禁止空值，下面的 CHECK 禁止空字符串，UNIQUE 禁止重复登记同一 Key。
    object_key VARCHAR(500) COLLATE utf8mb4_0900_bin NOT NULL,

    -- 人工控制的排序值，省略时为 0；本项目查询时采用升序，数值小的在前。
    -- 没有唯一约束，所以可以相同；同值时查询再按 id 排序。
    -- 当前没有“必须非负”的 CHECK，SQL 定义本身没有禁止负数。
    sort_order INT NOT NULL DEFAULT 0,

    -- 图片宽、高，单位为像素；INT 是 4 字节整数。
    -- 未读取到尺寸时，两者一起保存 NULL，不能用 0 伪装“未知”。
    -- 配对与正数要求由下面的 ck_image_dimensions 检查。
    width INT NULL,
    height INT NULL,

    -- 图片登记时间，约定 UTC、精确到毫秒；插入记录时显式填写。
    created_at DATETIME(3) NOT NULL,

    -- 图片记录的主键，与 portfolio 表自己的主键各自独立。
    PRIMARY KEY (id),

    -- 整张图片表内 object_key 唯一：不同 id 也不能登记同一个 Key。
    -- 这条规则使本项目中的一个对象 Key 只登记一次，不能同时挂到另一件作品。
    -- 比较使用本列的 COLLATE，因此大小写不同的 Key 可以分别登记。
    UNIQUE KEY uq_image_object_key (object_key),

    -- 面向“查某作品的图片，并按类型/顺序/id 组织结果”的联合索引。
    -- 最左列是 portfolio_id，可为所属作品查找和下方外键提供索引支持。
    -- 业务要 work 在 scene 前，但字符串升序会把 scene 排在 work 前。
    -- 因此教程的查询使用 CASE 指定类型顺序；该表达式排序未必能直接复用整个索引。
    KEY ix_image_order (portfolio_id, image_type, sort_order, id),

    -- 外键读法：本表 portfolio_id 的非空值必须出现在 portfolio.id 中。
    -- 例如作品 1001 存在，图片可填 portfolio_id=1001；不存在的 9999 会被拒绝。
    -- ON DELETE RESTRICT：有图片引用时，拒绝删除被引用的作品。
    -- ON UPDATE RESTRICT：有图片引用时，拒绝修改该作品被引用的 id。
    -- UPDATE 的限制针对引用键，不会因此禁止修改作品标题或描述。
    -- 是否允许删除某张图片，还要看稍后添加的“作品封面指向图片”的另一条外键。
    CONSTRAINT fk_image_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES portfolio (id) ON DELETE RESTRICT ON UPDATE RESTRICT,

    -- image_type 不允许拼写成其他值，例如 'cover'；封面由作品表的引用来指定。
    CONSTRAINT ck_image_type CHECK (image_type IN ('work', 'scene')),

    -- 这里只保证 Key 长度大于 0，并未检查 OSS 对象是否存在或路径是否合法。
    -- 这里没有 TRIM，所以全是空格的字符串也可能满足这个 CHECK。
    -- 对象 Key 需要按原值使用，额外格式和对象存在性检查由后续业务流程负责。
    CONSTRAINT ck_image_key CHECK (CHAR_LENGTH(object_key) > 0),

    -- 尺寸允许两种状态：未知（NULL, NULL），或者已知（两个正整数）。
    -- 合法：(NULL, NULL)、(1200, 800)。
    -- 非法：(1200, NULL)、(NULL, 800)、(0, 800)、(-1, 800)。
    -- 只写 width > 0 AND height > 0 不够：遇到 NULL 可能产生 UNKNOWN。
    -- 这里显式判断两者 IS NOT NULL，使“只填一边”能够被确定地拒绝。
    CONSTRAINT ck_image_dimensions CHECK (
        (width IS NULL AND height IS NULL)
        OR
        (width IS NOT NULL AND height IS NOT NULL AND width > 0 AND height > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- ============================================================================
-- 3. 给已存在的作品表补充封面外键（这是修改表，不是创建第六张业务表）
-- ============================================================================
-- ALTER TABLE portfolio：修改 portfolio 的结构。
-- ADD CONSTRAINT：给现有表增加一个命名约束。
-- 两张表此时都已存在，可以建立“作品的封面引用图片”的关系。
-- cover_image_id 为 NULL 时表示暂未选封面；有值时必须指向存在的图片 id。
--
-- 这条外键的边界很重要：
--   能保证：被引用的图片记录存在。
--   不能保证：这张图属于当前作品；这张图的 image_type 是 work。
-- 后两条需要 D4 的 Java Service 校验；只添加外键不等于发布规则已经实现。
--
-- ON DELETE RESTRICT：图片仍被某作品当作封面时，拒绝删除该图片。
-- ON UPDATE RESTRICT：图片仍被引用时，拒绝修改该图片被引用的 id。
-- 它们不会自动级联删除作品，也不会把作品的封面字段自动设成 NULL。
--
-- 将来创建作品的顺序也遵循这个关系：
--   先插入草稿（封面 NULL）-> 登记它的图片 -> 填封面图片 id -> 校验后发布。
ALTER TABLE portfolio ADD CONSTRAINT fk_portfolio_cover
    FOREIGN KEY (cover_image_id) REFERENCES portfolio_image (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

-- ============================================================================
-- 4. tag_group：标签组表（例如“题材”“地点类型”）
-- ============================================================================
CREATE TABLE tag_group (
    -- 标签组自身的唯一编号，例如 11 代表“题材”，12 代表“地点类型”。
    id BIGINT NOT NULL AUTO_INCREMENT,

    -- 标签组名称，最多 50 个字符；不能为 NULL。
    name VARCHAR(50) NOT NULL,

    -- 标签组在页面中的排列顺序；查询按 sort_order、id 排序，默认排序值为 0。
    -- 保存排序值本身不会让没有 ORDER BY 的 SELECT 自动按这个值返回。
    sort_order INT NOT NULL DEFAULT 0,

    -- 标签组记录创建时间，约定 UTC；插入时需要显式填写。
    created_at DATETIME(3) NOT NULL,

    PRIMARY KEY (id),

    -- 标签组名称在整个 tag_group 表中唯一，不能有两个同名的“题材”组。
    -- 名称比较也遵循 utf8mb4_0900_bin，因此区分大小写和尾部空格。
    -- 应用要先去掉名称两端空白再保存，避免“题材”和“题材 ”这类业务重复。
    UNIQUE KEY uq_tag_group_name (name),

    -- 禁止空字符串或仅由普通空格组成的组名；此检查不会自动修改原始 name。
    CONSTRAINT ck_tag_group_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- ============================================================================
-- 5. tag：标签表（例如“题材”组中的“街拍”“风景”）
-- ============================================================================
CREATE TABLE tag (
    -- 标签自身的编号，例如 111 代表“街拍”，112 代表“风景”。
    id BIGINT NOT NULL AUTO_INCREMENT,

    -- 所属标签组的 id，例如街拍和风景的 group_id 都可以是 11。
    -- group_id 允许重复，实现“标签组一对多标签”；不能为 NULL，组也必须存在。
    group_id BIGINT NOT NULL,

    -- 标签名称，如“街拍”；同组能否重名由下面的联合唯一索引决定。
    name VARCHAR(50) NOT NULL,

    -- 标签在组内的展示顺序，默认 0；查询排序相同时再用 id 区分。
    sort_order INT NOT NULL DEFAULT 0,

    -- 标签记录创建时间，约定 UTC；插入时显式填写。
    created_at DATETIME(3) NOT NULL,

    PRIMARY KEY (id),

    -- 联合唯一索引要求“group_id 与 name 的组合”不重复，不是各列分别唯一。
    -- (11, '街拍') 和 (11, '风景') 可以共存；再插入 (11, '街拍') 会失败。
    -- (12, '街拍') 不会因这条约束被拒绝，因为属于另一个组。
    -- 该索引最左列为 group_id，也能支持所属组查找及下面的外键。
    -- 索引名作用于当前表，因此可以与 tag_group 表的同名索引共存。
    UNIQUE KEY uq_tag_group_name (group_id, name),

    -- id 已经是主键，因此 id 本来就唯一；这里显式声明 (id, group_id) 也唯一。
    -- 目的是为下一张表的组合外键提供列顺序匹配的唯一目标键。
    -- 这个唯一键同时表达“这个标签 id 与它实际所属组的组合”。
    -- 如果只分别检查标签 id 和组 id 存在，就无法证明两者是正确的一对。
    UNIQUE KEY uq_tag_id_group (id, group_id),

    -- tag.group_id -> tag_group.id：每个标签必须属于一个已经存在的组。
    -- 一个组仍有标签引用时，不能直接删除该组或修改该组的 id。
    CONSTRAINT fk_tag_group FOREIGN KEY (group_id)
        REFERENCES tag_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,

    -- 标签名不能是空字符串或仅有普通空格；输入清理仍由应用负责。
    CONSTRAINT ck_tag_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- ============================================================================
-- 6. portfolio_tag：作品选标签的关联表
-- ============================================================================
-- 作品与标签整体是多对多关系：一个作品能选多个组的标签，一个标签能被多个作品选。
-- 本项目再加一条业务限制：同一个作品，在同一个标签组中，最多选择一个标签。
-- 因此一行不是“一个标签的定义”，而是“一次作品对标签的选择”。
--
-- 示例（假设相关作品、组和标签都已存在）：
--   portfolio_id | tag_group_id | tag_id
--   1001         | 11           | 111     -> 《雨夜》在“题材”组选“街拍”
--   1001         | 12           | 121     -> 《雨夜》在“地点类型”组选“城市”
--   1002         | 11           | 111     -> 另一个作品也能选“街拍”
-- 这些只是注释中的示例，不会插入数据库。
CREATE TABLE portfolio_tag (
    -- 哪一个作品在选标签；参与组合主键，也有指向作品表的独立外键。
    portfolio_id BIGINT NOT NULL,

    -- 在哪一个标签组中选择；参与组合主键，同时参与与 tag_id 配对的组合外键。
    -- 此列没有单独指向 tag_group 的外键，而是通过 tag 的正确归属间接保证组存在。
    tag_group_id BIGINT NOT NULL,

    -- 具体选中了哪个标签；与 tag_group_id 一起指向 tag 的 (id, group_id)。
    tag_id BIGINT NOT NULL,

    -- 整个表只有这一个主键约束，主键由两个字段一起组成，不需要再单独添加 id。
    -- (1001, 11) 只能出现一次，所以作品 1001 在组 11 里最多有一条选择记录。
    -- 已选标签 111 后，再插入 (1001, 11, 112) 会因主键重复失败。
    -- 如果把主键换成 (portfolio_id, tag_id)，111 和 112 不同，就挡不住同组双选。
    -- “最多一个”允许完全没有选择，这条主键不会强迫每个作品选满所有组。
    PRIMARY KEY (portfolio_id, tag_group_id),

    -- 普通联合索引，面向“按某个标签找作品”的查询。
    -- 例如 WHERE tag_id = 111 可以用 tag_id 作为最左查找条件，并取到 portfolio_id。
    -- 它不是唯一索引，同一标签可以出现在许多作品的关联记录中。
    KEY ix_portfolio_tag_filter (tag_id, portfolio_id),

    -- 为下面的组合外键显式提供子表索引，索引前两列与外键列顺序一致。
    -- 上一个索引的第二列是 portfolio_id，不能代替 (tag_id, tag_group_id) 的配套索引。
    -- 索引负责支持查找；真正检查“标签与组必须匹配”的是下面的 FOREIGN KEY。
    KEY ix_portfolio_tag_group_fk (tag_id, tag_group_id),

    -- 关联记录所指的作品必须存在。
    -- 有关联记录引用作品时，RESTRICT 拒绝直接删除该作品或修改其 id。
    -- 组合主键以 portfolio_id 开头，已为本条外键提供相应的子表索引。
    CONSTRAINT fk_portfolio_tag_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES portfolio (id) ON DELETE RESTRICT ON UPDATE RESTRICT,

    -- 组合外键：把两个值当成一对，到同一条 tag 记录里验证。
    -- 对应顺序是：tag_id -> tag.id，tag_group_id -> tag.group_id。
    -- 如果 tag 中有 (id=111, group_id=11)，那么 (tag_id=111, tag_group_id=11) 合法。
    -- (tag_id=111, tag_group_id=12) 不合法，即使标签 111 和组 12 分别都存在。
    -- 这保证“选的标签确实属于所记录的组”，与上面的“同组最多选一次”各司其职。
    -- 两列都是 NOT NULL，所以不能靠留空绕开这一对值的归属校验。
    -- 有关联记录引用时，删除标签、修改其 id 或 group_id 都可能被 RESTRICT 阻止。
    -- 修改标签名不改变被引用的 (id, group_id)，不会仅因本外键被禁止。
    CONSTRAINT fk_portfolio_tag_membership FOREIGN KEY (tag_id, tag_group_id)
        REFERENCES tag (id, group_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- ============================================================================
-- 读完后的自查（全部是注释，不会执行）
-- ============================================================================
-- 1. 五张表一共 31 个字段、5 个主键；有 5 个普通索引、4 个唯一索引、5 条外键、9 个 CHECK。
--    本文件只有 6 条可执行语句：5 条 CREATE TABLE，加 1 条 ALTER TABLE。
-- 2. 主键和唯一索引防重复；外键保护引用关系；CHECK 检查当前行里的值是否合法。
--    这些规则会在写入时生效，不需要等查询时才检查。
-- 3. 外键有方向：图片指向作品，与作品用 cover_image_id 指向图片，是两条不同的关系。
--    RESTRICT 限制的是仍被引用的父记录/父键操作，不是把整个表锁成不能修改。
-- 4. “已发布作品必须有本作品的 work 封面”、拍摄时间的精度规范化、
--    OSS 对象真实存在性、公开查询只返回 published，都需要后续业务代码处理。
-- 5. 建表只定义结构；创建成功后，五张业务表最初没有作品或标签数据。
--    后续练习数据放在教程指定的本地示例脚本中，不放进本迁移。
--
-- 启动应用完成迁移后，可在 MySQL 客户端手动运行下面这些只读检查：
--   SHOW TABLES;
--   SELECT version, description, success FROM flyway_schema_history;
--   SHOW CREATE TABLE portfolio_tag;
--   SHOW INDEX FROM portfolio_image;
-- SHOW TABLES 通常会看到五张业务表，加上 Flyway 的迁移历史表。
-- SHOW CREATE TABLE 展示 MySQL 实际保存的建表定义，可核对外键和 CHECK 是否存在。
-- SHOW INDEX 展示索引；例如唯一索引的 Non_unique 为 0，普通索引为 1。
--
-- 可回看的 MySQL 8.4 官方资料：
-- [1] CHECK 与 NULL / UNKNOWN：
--     https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html
-- [2] 外键、组合引用和 RESTRICT：
--     https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html
-- [3] utf8mb4_0900_bin 与尾部空格比较：
--     https://dev.mysql.com/doc/refman/8.4/en/charset-binary-collations.html
