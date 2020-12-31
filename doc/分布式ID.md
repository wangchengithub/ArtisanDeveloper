## 分布式ID的意义
数据分库分表后需要有一个唯一ID来标识一条数据或消息
## 分布式ID的要求
*  全局唯一性：不能出现重复的ID号，这是最基本的要求。
* 数据递增：MySQL主键聚簇索引
* 信息安全：单调递增的话，会造成信息泄露，如：订单编号，早晚各下一笔订单就知道了一天的订单量
* 高可用高QPS：TP999（Top Percentile）延迟都要尽可能低
## 常见的生成策略
### UUID
UUID(Universally Unique Identifier)的标准型式包含32个16进制数字，[生成策略](https://www.ietf.org/rfc/rfc4122.txt)
##### 优点
* 性能非常高：本地生成，没有网络消耗   [高并发UUID性能问题](https://youyou-tech.com/2019/12/14/Java%E9%81%BF%E5%9D%91%E6%8C%87%E5%8D%97%E2%80%94%E2%80%94%E9%AB%98%E5%B9%B6%E5%8F%91%E5%9C%BA%E6%99%AF%E4%B8%8B%E7%9A%84%E6%80%A7%E8%83%BD%E9%9A%90/)
* 可以作为sign，对外暴露时替换自增ID
##### 缺点
* 无序不能作为数据库主键
* 信息不安全：泄露MAC地址
### Redis
```
-- 64 bit signed integers
redis> SET mykey "10"  
"OK"
redis> INCRBY mykey 5
(integer) 15
```
#### 优点
* 简单 效率高
#### 缺点
* 持久化 AOF(取决fsync策略，如果是everysec，最多丢失1秒的数据) RDB(快照，fork一个子进程来完成)
* [内存淘汰策略](https://redis.io/topics/lru-cache)
### 数据库自增主键
[mysql 8.0](https://dev.mysql.com/doc/refman/8.0/en/innodb-auto-increment-handling.html) 
* 三种策略
    * innodb_autoinc_lock_mode = 0 (“traditional” lock mode) 
In this lock mode, all “INSERT-like” statements obtain a special table-level AUTO-INC lock for inserts into tables with AUTO_INCREMENT columns. `This lock is normally held to the end of the statement (not to the end of the transaction) `to ensure that auto-increment values are assigned in a predictable and repeatable order for a given sequence of INSERT statements, and to ensure that auto-increment values assigned by any given statement are consecutive.
    * innodb_autoinc_lock_mode = 1 (“consecutive” lock mode) Prior to MySQL 8.0的默认值
In this mode, “bulk inserts” use the special AUTO-INC table-level lock and hold it until the end of the statement. 
“Simple inserts” (for which the number of rows to be inserted is known in advance) avoid table-level AUTO-INC locks by obtaining the required number of auto-increment values under the control of a mutex (a light-weight lock) that is only held for the duration of the allocation process, not until the statement completes. No table-level AUTO-INC lock is used unless an AUTO-INC lock is held by another transaction. If another transaction holds an AUTO-INC lock, a “simple insert” waits for the AUTO-INC lock, as if it were a “bulk insert”.
    * innodb_autoinc_lock_mode = 2 (“interleaved” lock mode)  MySQL 8.0的默认值
In this lock mode, no “INSERT-like” statements use the table-level AUTO-INC lock, and multiple statements can execute at the same time. This is the fastest and most scalable lock mode, but it is not safe when using statement-based replication or recovery scenarios when SQL statements are replayed from the binary log.
* MySQL 5.7 and earlier 是存储在内存中的重启时通过·SELECT MAX(ai_col) FROM table_name FOR UPDATE;·初始化  mysql 8.0 放在redo_log中

自增主键问题
https://www.cnblogs.com/zhoujinyi/p/3433823.html
https://zhuanlan.zhihu.com/p/84046604
#### 单点(美团)
```sql
-- 如果表中的一个旧记录与一个用于PRIMARY KEY或一个UNIQUE索引的新记录具有相同的值，则在新记录被插入之前，旧记录被删除
-- id 主键，stub唯一
begin;
REPLACE INTO Tickets64 (stub) VALUES ('a');
SELECT LAST_INSERT_ID();
commit;
```
##### 优点
* 非常简单，利用现有数据库系统的功能实现，成本小
* ID号单调自增，可以实现一些对ID有特殊要求的业务
##### 缺点
* 强依赖DB，当DB异常时整个系统不可用
* ID发号性能瓶颈限制在单台MySQL的读写性能
#### 多节点
在分布式系统中部署多台台机器，每台机器设置不同的初始值，且步长和机器数相等。比如有两台机器。设置步长step为2，TicketServer1的初始值为1（1，3，5，7，9，11…）、TicketServer2的初始值为2（2，4，6，8，10…）
##### 缺点
* 系统水平扩展比较困难
* 只能靠堆机器来提高性能
#### segment(美团leaf) [官方文档地址](https://tech.meituan.com/2017/04/21/mt-leaf.html)
获取一个segment(step决定大小)号段的值。用完之后再去数据库获取新的号段，这样可以大大的减轻数据库的压力。各个业务不同的发号需求用biz_tag字段来区分，每个biz-tag的ID获取相互隔离，互不影响。

##### 优点
* Leaf服务可以很方便的线性扩展，性能完全能够支撑大多数业务场景。
* ID号码是趋势递增的8byte的64位数字，满足上述数据库存储的主键要求。
* 容灾性高：Leaf服务内部有号段缓存，即使DB宕机，短时间内Leaf仍能正常对外提供服务。
* 可以自定义max_id的大小，非常方便业务从原有的ID方式上迁移过来。
##### 缺点
* ID号码不够随机，能够泄露发号数量的信息，不太安全。
* TP999数据波动大，当号段使用完之后还是会hang在更新数据库的I/O上，tg999数据会出现偶尔的尖刺。
* DB宕机会造成整个系统不可用。
* 多台服务的时候只能保证趋势递增
##### 双buffer
Leaf 取号段的时机是在号段消耗完的时候进行的，也就意味着号段临界点的ID下发时间取决于下一次从DB取回号段的时间，并且在这期间进来的请求也会因为DB号段没有取回来，导致线程阻塞。如果请求DB的网络和DB的性能稳定，这种情况对系统的影响是不大的，但是假如取DB的时候网络发生抖动，或者DB发生慢查询就会导致整个系统的响应时间变慢。
为此，我们希望DB取号段的过程能够做到无阻塞，不需要在DB取号段的时候阻塞请求线程，即当号段消费到某个点时就异步的把下一个号段加载到内存中。而不需要等到号段用尽的时候才去更新号段。这样做就可以很大程度上的降低系统的TP999指标。详细实现如下图所示：

* 每个biz-tag都有消费速度监控，通常推荐segment长度设置为服务高峰期发号QPS的600倍（10分钟），这样即使DB宕机，Leaf仍能持续发号10-20分钟不受影响。
* 每次请求来临时都会判断下个号段的状态，从而更新此号段，所以偶尔的网络抖动不会影响下个号段的更新。

[至于为什么是10%，完全是脑暴](https://github.com/Meituan-Dianping/Leaf/issues/20)
##### Leaf动态调整Step
更新间隔<15分钟step*2，否则step/2
##### Leaf高可用容灾
Mysql主从同步，美团目前采取一主两从的方式，分机房部署的方式
`如果不是单独的服务，要注意获取ID的操作不要在同一个事务中,如果使用require_new的事务隔离级别，要注意外层事务的数据库链接是不会释放的，高并发情况下可能会有问题`
### 类snowflake
snowflake是twitter开源的分布式ID生成算法，依赖谷歌框架，现在已经retired ，[git地址](https://github.com/twitter-archive/snowflake)

* 第一个bit位是标识部分，在java中由于long的最高位是符号位，正数是0，负数是1，一般生成的ID为正数，所以固定为0。
* 时间戳部分占41bit，这个是毫秒级的时间，一般实现上不会存储当前的时间戳，而是时间戳的差值（当前时间-固定的开始时间），这样可以使产生的ID从更小值开始；41位的时间戳可以使用69年，(1L << 41) / (1000L * 60 * 60 * 24 * 365) = 69年
* 工作机器id占10bit，比较灵活，比如：可以使用前5位作为数据中心机房标识，后5位作为单机房机器标识，也可以部署1024个节点。
* 序列号部分占12bit，支持同一毫秒内同一个节点可以生成4096个ID
##### 优点
* 不依赖数据库等第三方系统，稳定性高，性能高
* 可以根据自身业务特性分配bit位，非常灵活
##### 缺点
* 强依赖机器时钟，如果机器上时钟回拨，会导致发号重复或者服务会处于不可用状态
### 美团leaf
“There are no two identical leaves in the world” --德国哲学家、数学家莱布尼茨 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [git地址](https://github.com/Meituan-Dianping/Leaf)
Leaf-snowflake方案完全沿用snowflake方案的bit位设计，即“1+41+10+12”的方式组装ID号。使用Zookeeper的持久顺序节点配置wokerID
#### zookeeper节点类型
[官方文档](https://zookeeper.apache.org/doc/current/zookeeperProgrammers.html#ch_zkDataModel)
* 永久节点(Persist Nodes)：节点创建后会被持久化，只有主动调用delete方法的时候才可以删除节点。
* 临时节点(Ephemeral Nodes)：节点创建后在创建者超时连接或失去连接的时候，节点会被删除。临时节点下不能存在子节点。
* 顺序节点(Sequence Nodes)：创建的节点名称后自动添加序号，如节点名称为"node-"，自动添加为"node-1"，顺序添加为"node-2"

以上三种节点组合可产生：PERSISTENT、PERSISTENT_SEQUENTIAL、EPHEMERAL、EPHEMERAL_SEQUENTIAL四种节点。
3.5.3开始增加了两种节点：
* Container Nodes：Container znodes are special purpose znodes useful for recipes such as leader, lock, etc. When the last child of a container is deleted, the container becomes a candidate to be deleted by the server at some point in the future.
Given this property, you should be prepared to get KeeperException.NoNodeException when creating children inside of container znodes. i.e. when creating child znodes inside of container znodes always check for KeeperException.NoNodeException and recreate the container znode when it occurs.
* TTL Nodes：When creating PERSISTENT or PERSISTENT_SEQUENTIAL znodes, you can optionally set a TTL in milliseconds for the znode. If the znode is not modified within the TTL and has no children it will become a candidate to be deleted by the server at some point in the future.

zookeeper顺序节点能否用来生成分布式唯一ID？只支持32，性能不高
#### 获取workerId
* 启动Leaf-snowflake服务，连接Zookeeper，在leaf_forever父节点下检查自己是否已经注册过（是否有该顺序子节点）。
* 如果有注册过直接取回自己的workerID（zk顺序节点生成的int类型ID号），启动服务。
* 如果没有注册过，就在该父节点下面创建一个持久顺序节点，创建成功后取回顺序号当做自己的workerID号，启动服务。

`注意美团从zookeeper解析workerId时直接按‘-’分割取的第二个，所以zookeeper节点名称不要带‘-’`
`workerId直接取的节点序号 int 会超过1023，但是集群中不应该有这么多机器`[ issue](https://github.com/Meituan-Dianping/Leaf/issues/57)
`取zookeeper上的wokerId是通过ip来的，重启需要保证ip不变，不然会浪费wokerId` 
#### 弱依赖ZooKeeper
除了每次会去ZK拿数据以外，也会在本机文件系统上缓存一个workerID文件。当ZooKeeper出现问题，恰好机器出现问题需要重启时，能保证服务能够正常启动。
#### 序列号的生成
美团在每毫秒开始时的第一个序列号采用了`RANDOM.nextInt(100)`目的是不想让最后一个数字都是0, 可用ThreadLocalRandom 替换Random [issue](https://github.com/Meituan-Dianping/Leaf/issues/52)
#### 解决时钟问题
因为这种方案依赖时间，如果机器的时钟发生了回拨，那么就会有可能生成重复的ID号，需要解决时钟回退的问题。
服务启动时首先检查自己是否写过ZooKeeper leaf_forever节点：
* 若写过，则用自身系统时间与leaf_forever/${self}节点记录时间做比较，若小于leaf_forever/${self}时间则认为机器时间发生了大步长回拨，服务启动失败并报警。
* 若未写过，证明是新服务节点，直接创建持久节点leaf_forever/${self}并写入自身系统时间，接下来综合对比其余Leaf节点的系统时间来判断自身系统时间是否准确，具体做法是取leaf_temporary下的所有临时节点(所有运行中的Leaf-snowflake节点)的服务IP：Port，然后通过RPC请求得到所有节点的系统时间，计算sum(time)/nodeSize。
* 若abs( 系统时间-sum(time)/nodeSize ) < 阈值，认为当前系统时间准确，正常启动服务，同时写临时节点leaf_temporary/${self} 维持租约。
* 否则认为本机系统时间发生大步长偏移，启动失败并报警。
* 每隔一段时间(3s)上报自身系统时间写入leaf_forever/${self}。

`第二部和第三步在开源项目中没有体现`
由于强依赖时钟，对时间的要求比较敏感，在机器工作时NTP同步也会造成秒级别的回退，建议可以直接关闭NTP同步。要么在时钟回拨的时候直接不提供服务直接返回ERROR_CODE，等时钟追上即可。或者做一层重试，然后上报报警系统，更或者是发现有时钟回拨之后自动摘除本身节点并报警。
#### 美团应用
从上线情况来看，在2017年闰秒出现那一次出现过部分机器回拨，由于Leaf-snowflake的策略保证，成功避免了对业务造成的影响。
Leaf在美团点评公司内部服务包含金融、支付交易、餐饮、外卖、酒店旅游、猫眼电影等众多业务线。目前Leaf的性能在4C8G的机器上`QPS能压测到近5w/s，TP999 1ms`，已经能够满足大部分的业务的需求。每天提供亿数量级的调用量，作为公司内部公共的基础技术设施，必须保证高SLA和高性能的服务，我们目前还仅仅达到了及格线，还有很多提高的空间。
#### LOCK应该写在try catch里面还是外面？
[美团isssue](https://github.com/Meituan-Dianping/Leaf/issues/58#event-2671104693)  [修改提交](https://github.com/Meituan-Dianping/Leaf/commit/2005779538264bcb2048c10b2e25c0ea00a28678?diff=split)
[阿里p3c issue](https://github.com/alibaba/p3c/issues/287) 阿里采纳了放在try外面的建议并加入开发手册
[java doc](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/Lock.html) 放在try外面
### 百度UidGenerator
[git地址](https://github.com/baidu/uid-generator)
UidGenerator是Java实现的, 基于Snowflake算法的唯一ID生成器。UidGenerator以组件形式工作在应用项目中, 支持自定义workerId位数和初始化策略, 从而适用于docker等虚拟化环境下实例自动重启、漂移等场景。 在实现上, UidGenerator通过借用未来时间来解决sequence天然存在的并发限制; 采用`RingBuffer`来缓存已生成的UID, 并行化UID的生产和消费, `同时对CacheLine补齐，避免了由RingBuffer带来的硬件级「伪共享」问题. 最终单机QPS可达600万`。
#### 1+ 28 +22 + 13 的位数分配
* sign(1bit) 固定1bit符号标识，即生成的UID为正数。
* delta seconds (28 bits) 当前时间，相对于时间基点"2016-05-20"（可配置）的增量值，`单位：秒`，最多可支持约8.7年
* worker id (22 bits) 机器id，最多可支持约420w次机器启动。内置实现为在启动时由数据库分配，默认分配策略为用后即弃，后续可提供复用策略。
* sequence (13 bits)  每秒下的并发序列，13 bits可支持每秒8192个并发。
#### workerId
默认采用`DisposableWorkerIdAssigner`,实例每次启动会往数据库中插入一条记录，拿到自增ID作为workerId,
`避免了时间的持久化 厉害`
```sql
CREATE TABLE WORKER_NODE(
ID BIGINT NOT NULL AUTO_INCREMENT COMMENT 'auto increment id',
HOST_NAME VARCHAR(64) NOT NULL COMMENT 'host name',
PORT VARCHAR(64) NOT NULL COMMENT 'port',
TYPE INT NOT NULL COMMENT 'node type: ACTUAL or CONTAINER',
LAUNCH_DATE DATE NOT NULL COMMENT 'launch date',
MODIFIED TIMESTAMP NOT NULL COMMENT 'modified time',
CREATED TIMESTAMP NOT NULL COMMENT 'created time',
PRIMARY KEY(ID)
)COMMENT='DB WorkerID Assigner for UID Generator',ENGINE = INNODB;
```
#### sequence:DefaultUidGenerator
* synchronized保证线程安全；
* 如果时间有任何的回拨，那么直接抛出异常；
* 如果当前时间和上一次是同一秒时间，那么sequence自增。如果同一秒内自增值超过2^13-1，那么就会自旋等待下一秒（getNextSecond）；
* 如果是新的一秒，那么sequence重新从0开始；
#### CachedUidGenerator
核心利用了两个RingBuffer：
* 一个保存唯一ID：Tail指针表示最后一个生成的唯一ID。如果这个指针追上了Cursor指针，意味着RingBuffer已经满了。这时候，不允许再继续生成ID了。用户可以通过属性rejectedPutBufferHandler指定处理这种情况的策略。Cursor指针表示最后一个已经给消费的唯一ID。如果Cursor指针追上了Tail指针，意味着RingBuffer已经空了。这时候，不允许再继续获取ID了。用户可以通过属性rejectedTakeBufferHandler指定处理这种异常情况的策略。
* 一个保存flag，0表示`CAN_PUT_FLAG`,1 表示`CAN_TAKE_FLAG`.

RingBuffer的尺寸是2^n，n必须是正整数。参考`disruptor 的原理`

##### 初始化
CachedUidGenerator在初始化时除了给workerId赋值，还会初始化RingBuffer。这个过程主要工作有：
* 根据boostPower的值确定RingBuffer的size；
* 构造RingBuffer，默认paddingFactor为50。这个值的意思是当RingBuffer中剩余可用ID数量少于50%的时候，就会触发一个异步线程往RingBuffer中填充新的唯一ID（调用BufferPaddingExecutor中的paddingBuffer()方法，这个线程中会有一个标志位running控制并发问题），直到填满为止；
* 判断是否配置了属性scheduleInterval，这是另外一种RingBuffer填充机制, 在Schedule线程中, 周期性检查填充。默认:不配置, 即不使用Schedule线程. 如需使用, 请指定Schedule线程时间间隔, 单位:秒；
* 初始化Put操作拒绝策略，对应属性rejectedPutBufferHandler。即当RingBuffer已满, 无法继续填充时的操作策略。默认无需指定, 将丢弃Put操作, 仅日志记录. 如有特殊需求, 请实现RejectedPutBufferHandler接口(支持Lambda表达式)；
* 初始化Take操作拒绝策略，对应属性rejectedTakeBufferHandler。即当环已空, 无法继续获取时的操作策略。默认无需指定, 将记录日志, 并抛出UidGenerateException异常. 如有特殊需求, 请实现RejectedTakeBufferHandler接口；
* `初始化填满RingBuffer中所有slot`（即塞满唯一ID，这一步和第2步骤一样都是调用BufferPaddingExecutor中的paddingBuffer()方法）；
* 开启buffer补丁线程（前提是配置了属性scheduleInterval），原理就是利用ScheduledExecutorService的scheduleWithFixedDelay()方法。

`通过时间值递增得到新的时间值（lastSecond.incrementAndGet()），而不是System.currentTimeMillis()这种方式，而lastSecond是AtomicLong类型，所以能保证线程安全问题。彻底解决了时间回调的问题`
#### 伪共享？
两个ringbuffer，一个是long,一个是PaddedAtomicLong，原因是slots数组选用原生类型是为了高效地读取，数组在内存中是连续分配的，当你读取第0个元素的之后，后面的若干个数组元素也会同时被加载。分析代码即可发现slots实质是属于多读少写的变量，所以使用原生类型的收益更高。而flags则是会频繁进行写操作，为了避免伪共享问题所以手工进行补齐
@sun.misc.Contended注解  jvm需要添加参数-XX:-RestrictContended
#### 滴滴tinyid
[git地址](https://github.com/didi/tinyid)
通过数据库生成，无snowflake方案
##### 与美团segment双buffer的不同点
##### 默认20%加载下个号段
##### 支持多db
 db只有一个master时，如果db不可用(down掉或者主从延迟比较大)，则获取号段不可用。实际上我们可以支持多个db，比如2个db，A和B，我们获取号段可以`随机从其中一台上获取`。那么如果A,B都获取到了同一号段，我们怎么保证生成的id不重呢？tinyid是这么做的，让A只生成偶数id，B只生产奇数id，对应的db设计增加了两个字段，如下所示

 delta代表id每次的增量，remainder代表余数，例如可以将A，B都delta都设置2，remainder分别设置为0，1则，A的号段只生成偶数号段，B是奇数号段。 通过delta和remainder两个字段我们可以根据使用方的需求灵活设计db个数，同时也可以为使用方提供只生产类似奇数的id序列。
##### 增加tinyid-client
使用http获取一个id，存在网络开销，是否可以本地生成id？为此我们提供了tinyid-client，我们可以向tinyid-server发送请求来获取可用号段，之后在本地构建双号段、id生成，如此id生成则变成纯本地操作，性能大大提升，因为本地有双号段缓存，则可以容忍tinyid-server一段时间的down掉，可用性也有了比较大的提升。
##### tinyid系统架构图

下面是一些关于这个架构图的说明:
* nextId和getNextSegmentId是tinyid-server对外提供的两个http接口
* nextId是获取下一个id，当调用nextId时，会传入bizType，每个bizType的id数据是隔离的，生成id会使用该bizType类型生成的IdGenerator。
* getNextSegmentId是获取下一个可用号段，tinyid-client会通过此接口来获取可用号段
* IdGenerator是id生成的接口
* IdGeneratorFactory是生产具体IdGenerator的工厂，每个biz_type生成一个IdGenerator实例。通过工厂，我们可以随时在db中新增biz_type，而不用重启服务
* IdGeneratorFactory实际上有两个子类IdGeneratorFactoryServer和IdGeneratorFactoryClient，区别在于，getNextSegmentId的不同，一个是DbGet,一个是HttpGet
* CachedIdGenerator则是具体的id生成器对象，持有currentSegmentId和nextSegmentId对象，负责nextId的核心流程。nextId最终通过AtomicLong.andAndGet(delta)方法产生。
## 参考文章
[Leaf——美团点评分布式ID生成系统](https://tech.meituan.com/2017/04/21/mt-leaf.html)
[Leaf：美团分布式ID生成服务开源](https://tech.meituan.com/2019/03/07/open-source-project-leaf.html)
[百度UID源码解析](http://blog.chriscs.com/2017/08/02/baidu-uid-generator/)
[UidGenerator：百度开源的分布式ID服务（解决了时钟回拨问题）](https://mp.weixin.qq.com/s/8NsTXexf03wrT0tsW24EHA)
[百度UID git](https://github.com/baidu/uid-generator/blob/master/README.zh_cn.md)
[美团技术文章-高性能队列——Disruptor](https://tech.meituan.com/2016/11/18/disruptor.html)
[disruptor 官方文档](http://lmax-exchange.github.io/disruptor/files/Disruptor-1.0.pdf)
[滴滴git官方文档](https://github.com/didi/tinyid/wiki/tinyid%E5%8E%9F%E7%90%86%E4%BB%8B%E7%BB%8D)
[一篇对伪共享、缓存行填充和CPU缓存讲的很透彻的文章](https://blog.csdn.net/qq_27680317/article/details/78486220)
## 临时参考文章
[高并发数据结构Disruptor解析](https://blog.csdn.net/zhxdick/article/details/52041876)
[Java内存访问重排序的研究](https://tech.meituan.com/2014/09/23/java-memory-reordering.html)
