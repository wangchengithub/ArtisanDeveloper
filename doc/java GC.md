#### java8内存结构

#### 垃圾识别
* 引用计数法（Reference Counting）
对每个对象的引用进行计数，每当有一个地方引用它时计数器 +1、引用失效则 -1，引用的计数放到对象头中，大于 0 的对象被认为是存活对象。虽然循环引用的问题可通过 Recycler 算法解决，但是在多线程环境下，引用计数变更也要进行昂贵的同步操作，性能较低，早期的编程语言会采用此算法。
* 可达性分析，又称引用链法（Tracing GC）
从 GC Root 开始进行对象搜索，可以被搜索到的对象即为可达对象，此时还不足以判断对象是否存活/死亡，需要经过多次标记才能更加准确地确定，整个连通图之外的对象便可以作为垃圾被回收掉。目前 Java 中主流的虚拟机均采用此算法。
GC Roots的对象包含以下几种：
    * 虚拟机栈（栈中的本地变量表）中引用的对象
    * 方法区中类静态属性引用的对象
    * 方法区中常量引用的对象
    * 本地方法栈JNI（即一般说的native方法）中引用的对象
#### 垃圾收集算法
##### Mark-Sweep（标记-清除）
 回收过程主要分为两个阶段，第一阶段为追踪（Tracing）阶段，即从 GC Root 开始遍历对象图，并标记（Mark）所遇到的每个对象，第二阶段为清除（Sweep）阶段，即回收器检查堆中每一个对象，并将所有未被标记的对象进行回收，整个过程不会发生对象移动。整个算法在不同的实现中会使用三色抽象（Tricolour Abstraction）、位图标记（BitMap）等技术来提高算法的效率，存活对象较多时较高效。

##### Mark-Compact （标记-整理）
这个算法的主要目的就是解决在非移动式回收器中都会存在的碎片化问题，也分为两个阶段，第一阶段与 Mark-Sweep 类似，第二阶段则会对存活对象按照整理顺序（Compaction Order）进行整理。主要实现有双指针（Two-Finger）回收算法、滑动回收（Lisp2）算法和引线整理（Threaded Compaction）算法等。

##### Copying（复制）
将空间分为两个大小相同的 From 和 To 两个半区，同一时间只会使用其中一个，每次进行回收时将一个半区的存活对象通过复制的方式转移到另一个半区。有递归（Robert R. Fenichel 和 Jerome C. Yochelson提出）和迭代（Cheney 提出）算法，以及解决了前两者递归栈、缓存行等问题的近似优先搜索算法。复制算法可以通过碰撞指针的方式进行快速地分配内存，但是也存在着空间利用率不高的缺点，另外就是存活对象比较大时复制的成本比较高。


三种算法在是否移动对象、空间和时间方面的一些对比，假设存活对象数量为 *L*、堆空间大小为 *H*，则：

把 mark、sweep、compaction、copying 这几种动作的耗时放在一起看，大致有这样的关系：

虽然 compaction 与 copying 都涉及移动对象，但取决于具体算法，compaction 可能要先计算一次对象的目标地址，然后修正指针，最后再移动对象。copying 则可以把这几件事情合为一体来做，所以可以快一些。另外，还需要留意 GC 带来的开销不能只看 Collector 的耗时，还得看 Allocator 。如果能保证内存没碎片，分配就可以用 pointer bumping 方式，只需要挪一个指针就完成了分配，非常快。而如果内存有碎片就得用 freelist 之类的方式管理，分配速度通常会慢一些。
##### 分代收集算法
根据对象存活周期的不同将内存划分为几块。一般是把java堆分成新生代和老年代，在新生代中，每次垃圾收集时都会发现有大批兑现死去，只有少量对象存活，那就选用复制算法，只需要付出少量存活对象的复制成本就可以完成收集。而老年代中因为对象存活率高、没有额外空间对它就行分配担保，就必须使用“标记-清理”或“标记-整理”算法来进行回收。
#### 算法实现中的相关概念
##### 枚举根节点（OopMap(Ordinary Object Pointer)）
可达性分析必须在一个能确保一致性的快照中进行。该点不满足的话分析结果准确性就无法保证。这点是导致GC进行时必须停顿所有Java执行线程的其中一个原因。
由于目前的主流Java虚拟机使用的都是准确式GC，所以当执行系统停顿下来后，并不需要一个不漏的检查完所有执行上下文和全局的引用位置，虚拟机应当是有方法直接得知哪些地方存放着对象引用。
在HotSpot的实现中是使用一组成为OopMap的数据结构来达到这个目的的，在类加载完成的时候，HotSpot就把对象内什么偏移量上是什么类型的数据计算出来，在JIT编译过程中也会在特定的位置记录下栈和寄存器中那些位置是引用。

`保守式GC和准确式GC可参考https://www.iteye.com/blog/rednaxelafx-1044951`·
##### 安全点
在OopMap的协助HotSpot下可以快速且准确的完成GC Roots的枚举，但一个很现实的问题随之而来：可能导致引用关系变化或者说OopMap内容变化的指令非常多，如果为每一条指令都生成对应的OopMap，那将需要大量的额外空间，这样GC的成本将会变的很高。实际上HotSpot也的却没有为每条指令都生成OopMap，而是只在“特定的位置”记录这些信息，这些位置便被称为安全点（Safepoint），`即程序执行时并非在所有地方都能停顿下来开始GC，只有在到达安全点时才能暂停`。Safepoint的选定既不能太少以致于让GC等待时间太长，也不能过于频繁以致于过分增大运行时的负荷。所以，安全点的选定基本上是以程序是否具有让程序长时间执行的特征为标准进行选定的——因为每条指令执行的时间都非常短暂，程序不太可能因为指令流长度太长这个原因而过长时间运行，长时间执行的最明显特征就是指令序列复用，例如方法调用、循环跳转、异常跳转等，所以具有这些功能的指令才会产生Safepoint。

对于Sefepoint，另一个需要考虑的问题是如何在GC发生时让所有线程（这里不包括执行JNI调用的线程）都“跑”到最近的安全点上再停顿下来。这里有两种方案可供选择：

* 抢先式中断（Preemptive Suspension）：抢先式中断不需要线程的执行代码主动去配合，在GC发生时，首先把所有线程全部中断，如果发现有线程中断的地方不在安全点上，就恢复线程，让它“跑”到安全点上。现在几乎没有虚拟机实现采用抢先式中断来暂停线程从而响应GC事件。

* 主动式中断（Voluntary Suspension）： 主动式中断的思想是当GC需要中断线程的时候，不直接对线程操作，仅仅简单地设置一个标志，各个线程执行时主动去轮询这个标志，发现中断标志为真时就自己中断挂起。轮询标志的地方和安全点是重合的，另外再加上创建对象需要分配内存的地方。

##### 安全区域

Safepoint机制保证了程序执行时，在不太长的时间内就会遇到可进入GC的Safepoint。但是，程序“不执行”的时候呢所谓的程序不执行就是没有分配CPU时间，典型的例子就是线程处于Sleep状态或Blocked状态，这时线程无法响应JVM的中断请求，“走到”安全的地方去中断挂起，对于这种情况就需要安全区域（Safe Region）来解决。



安全区域是指在一段代码片段之中，引用关系不会发生变化。在这个区域中的任意地方开始GC都是安全的。我们也可以把Safe Region看做是被扩展了的Safepoint。



在线程执行到Safe Region中的代码时，首先标识自己已经进入了Safe Region，那样，当在这段时间里JVM要发起GC时，就不用管标识自己为Safe Region状态的线程了。在线程要离开Safe Region时，它要检查系统是否已经完成了根节点枚举（或者是整个GC过程），如果完成了，那线程就继续执行，否则它就必须等待直到收到可以安全离开Safe Region的信号为止。

##### TLAB（Thread Local Allocation Buffer）线程本地分配缓存区
https://www.jianshu.com/p/8be816cbb5ed

##### 卡表
解决跨代引用（老年代引用新生代）
记忆集：https://blog.csdn.net/cold___play/article/details/105314966

在HotSpot的CMS和G1垃圾收集器中，都存在记忆集合，并且其实现就是卡表，由CardTableRS类定义，是GenRemSet类的子类。之前我们已经知道，卡表是个字节数组，每个字节对应堆空间老生代中的512个字节（这512个字节叫做卡页）是否有跨代引用。
HotSpot通过写屏障（write barrier）来维护卡表。

#### GC类型
* Young GC 新生代内存的垃圾收集
* Old GC，只清理老年代空间的GC事件，只有CMS的并发收集是这个模式
* Full GC，清理整个堆的GC事件，包括新生代、老年代、元空间等
* Mixed GC 清理整个新生代以及部分老年代的GC，只有G1有这个模式
#### GC主要过程
刚开始时，对象分配在 eden 区，s0（即：from）及 s1（即：to）区，几乎是空着，随着应用的运行，越来越多的对象被分配到 eden 区。
当 eden 区放不下时，就会发生 minor GC（也被称为 young GC），先标识出不可达垃圾对象，然后将可达对象，移动到 s0 区，然后将垃圾块清理掉，这一轮过后，eden 区就成空的了。
注：这里其实已经综合运用了“【标记-清理 eden】 + 【标记-复制 eden->s0】”算法。
随着时间推移，eden 如果又满了，再次触发 minor GC，同样还是先做标记，这时 eden 和 s0 区可能都有垃圾对象了（下图中的黄色块），注意：这时 s1（即：to）区是空的，s0 区和 eden 区的存活对象，将直接搬到 s1 区。然后将 eden 和 s0 区的垃圾清理掉，这一轮 minor GC 后，eden 和 s0 区就变成了空的了。
继续，随着对象的不断分配，eden 空可能又满了，这时会重复刚才的 minor GC 过程，不过要注意的是，这时候 s0 是空的，所以 s0 与 s1 的角色其实会互换，即：存活的对象，会从 eden 和 s1 区，向 s0 区移动。然后再把 eden 和 s1 区中的垃圾清除，这一轮完成后，eden 与 s1 区变成空的。
对于那些比较“长寿”的对象一直在 s0 与 s1 中挪来挪去，一来很占地方，而且也会造成一定开销，降低 gc 效率，于是有了“代龄(age)”及“晋升”。
对象在年青代的 3 个区(edge,s0,s1)之间，每次从 1 个区移到另 1 区，年龄+1，在 young 区达到一定的年龄阈值后，将晋升到老年代。jdk 1.8默认是6次

如果老年代，最终也放满了，就会发生 major GC（即 Full GC），由于老年代的的对象通常会比较多，因为标记-清理-整理（压缩）的耗时通常会比较长，会让应用出现卡顿的现象，这也是为什么很多应用要优化，尽量避免或减少 Full GC 的原因。
#### 垃圾收集器

* ParNew： 一款多线程的收集器，采用复制算法，主要工作在 Young 区，可以通过 -XX:ParallelGCThreads 参数来控制收集的线程数，整个过程都是 STW 的，常与 CMS 组合使用。
* CMS： 以获取最短回收停顿时间为目标，采用“标记-清除”算法，分 4 大步进行垃圾收集，其中初始标记和重新标记会 STW ，多数应用于互联网站或者 B/S 系统的服务器端上，JDK9 被标记弃用，JDK14 被删除，详情可见 JEP 363。    

    初始标记仅仅只是标记一下GC Roots能直接关联的对象，速度很快，并发标记阶段就是就行GC Roots Tracing的过程，而重新标记阶段则是为了修正并发标记期间因用户线程继续运作而导致标记产生变动的那一部分对象的标记记录，这个阶段的停顿时间一般会比初始标记阶段稍长一些，但远比并发标记的时间短。

    CMS缺点：
    * CMS对CPU资源非常敏感，在并发阶段会因为占用一部分线程（CPU资源）导致应用程序变慢，默认启动的回收线程数量是（CPU数量+3）/4
    * CMS收集器无法处理浮动垃圾
    * CMS是一款基于“标记-清除”算法实现的收集器,收集结束时会有大量空间碎片产生，为了解决这个问题，CMS收集器提供了-个
-XX:+UseCMSCompactAtFullCollection(默认是开启的),用于在CMS收集器顶不住要进行FullGC时，开启内存碎片的合并整理过程，内存整理的过程是无法并行的，空间碎片问题没有了，但停顿时间不得不变长。虚拟机还提供了另外一个参数-XX:CMSFullGCsBeforeCompaction=0（默认为0，表示每次进入Full GC时都进行碎片整理）用于设置执行多少次不压缩的Full Gc 后跟着来一次带压缩的
```
CMS GC ! = FullGC, CMS FullGC的触发点
* 大对象分配时，年轻代不够，直接晋升到老年代，老年代也不够
* CMS GC失败，Coucrrent mode failure
    并发模式失败：当CMS在执行回收时，新生代发生垃圾回收，同时老年代又没有足够的空间容纳晋升的对象时，CMS 垃圾回收就会退化成单线程的Full GC。所有的应用线程都会被暂停，老年代中所有的无效对象都被回收
    晋升失败：当新生代发生垃圾回收，老年代有足够的空间可以容纳晋升的对象，但是由于空闲空间的碎片化，导致晋升失败，此时会触发单线程且带压缩动作的Full GC
* 手动执行System.GC() 或jmap命令
* 次数达到CMSFullGCsBeforeCompaction
参考：
http://www.disheng.tech/blog/jvm-%E6%BA%90%E7%A0%81%E8%A7%A3%E8%AF%BB%E4%B9%8B-cms-%E4%BD%95%E6%97%B6%E4%BC%9A%E8%BF%9B%E8%A1%8C-full-gc/
https://www.cnblogs.com/onmyway20xx/p/6605324.html
```
#### 对象分配策略
* 对象优先在Eden区分配
大多数情况下，对象在先新生代Eden区中分配。当Eden区没有足够空间进行分配时，虚拟机将发起一次Young GC。
* 大对象直接进入老年代
虚拟机提供了一个对象大小阈值参数(-XX:PretenureSizeThreshold，默认值为0，代表不管多大都是先在Eden中分配内存)，大于参数设置的阈值值的对象直接在老年代分配，这样可以避免对象在Eden及两个Survivor直接发生大内存复制
* 长期存活的对象将进入老年代
对象每经历一次Minor GC，且没被回收掉，它的年龄就增加1，大于年龄阈值参数(-XX:MaxTenuringThreshold，jdk8 默认6)的对象，将晋升到老年代中
* 动态对象年龄判断
为了更好的适应不同程序的内存状况，虚拟机并不是永远的要求对象的年龄必须达到了MaxTenuringThreshold才能晋升老年代，如果在Survivor空间中相同年龄所有对象大小的总和大于Survivor大小的一半，年龄大于或等于该年龄的对象就可以直接进入老年代，无须等到MaxTenuringThreshold中要求的年龄。
* 空间分配担保
在Minor GC之前，虚拟机会检查老年代最大可用的连续空间是否大于新生代所有对象总空间，如果这个条件成立，那么可以确保Minor GC是安全的。如果不成立，虚拟机会检查HandlePromotionFailure设置值是否允许担保失败，如果允许那么会继续检查老年代最大可用的连续空间是否大于历次晋升到老年代对象的平均大小，如果大雨将尝试着进行一次Minor GC尽管这次GC是有风险的，如果小于或者HandlePromotionFailure不允许冒险，那么这次也会改为进行一次Full GC。
jdk6 update24之后 HandlePromotionFailure参数不会影响虚拟机的空间分配担保策略，规则变为只要老年代的连续空间大于新生代对象总大小或者历次晋升的平均大小就进行Minor GC，否则将进行Full GC
java8 中已经没有HandlePromotionFailure参数了
#### 参考资料
深入理解java虚拟机
[一文看懂JVM内存布局及GC原理](https://www.infoq.cn/article/3WyReTKqrHIvtw4frmr3)
https://tech.meituan.com/2020/11/12/java-9-cms-gc.html
https://tech.meituan.com/2018/11/01/cat-in-depth-java-application-monitoring.html