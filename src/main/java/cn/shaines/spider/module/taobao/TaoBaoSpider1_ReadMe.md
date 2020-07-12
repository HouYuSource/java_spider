@[toc]

## 1. 前言

上一篇博客 "爬虫让我再次在女同学面前长脸了~(现实版真实案例)" 说到了帮女同学批量下载试题，我把文章同步到了CSDN，竟然有41个赞 + 21个评论 + 155个收藏，难道大家和我的目的都一样：爬虫 liao mei ？

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705114649936.png)

本篇文章主要介绍如何“快速”抓取淘宝商品信息，从几个维度统计并且进行可视化，本次案例使用关键字 “**安踏篮球鞋男鞋**”，（仅仅是因为我最近买了一双鞋子，然后就想到了这个商品而已）

## 2. 故事的背景

没有故事，没有背景，就是突然想。。。

## 3. 爬虫的分类？

先来接地气几个词，一般来说爬虫可以分为 【通用爬虫 、垂直爬虫】，那么是如何定义的呢？
* 通用爬虫：通用爬虫不需要理会网站哪些资源是需要的，哪些是不需要的，一并抓取并将其文本部分做索引（比如：百度爬虫、搜狗爬虫。。。）
* 垂直爬虫：垂直爬虫往往在某一领域具有其专注性，并且垂直爬虫往往只需要其中一部分具有垂直性的资源，所以垂直爬虫相比通用爬虫更加精确，这也是比较常见的爬虫。

> 如果上面的解释还不够接地气的话，举个简单例子
小明想找一个女朋友，但是小明对女朋友要求为 null，只要 [ 是一个女的都行 ] 那种，这就可以理解为通用爬虫；
但是小明如果眼光比较挑，要求女朋友必须是 [ 身高165+，体重90，大眼睛，长头发，小蛮腰 ]，这就可以理解为垂直爬虫。

## 4. 淘宝对爬虫都有哪些限制？

淘宝爬虫，很多文章都在讲解淘宝登录，然后分析**ua参数**等，以前我也很执着去分析过，真的挺难，后面我就没有继续跟下去了，就使用浏览器驱动实现登录，如 selenium (需要修改参数，否则淘宝能识别) 或者 JxBrowser 等等。。。

除开淘宝登录来看，淘宝的底线还是比较低的，暂时没有很高防线，你**携带 cookie** 即可，没错就是携带“饼干”给淘宝，他就给你通行了，所以本篇博客是通过携带 cookie 进行快速获取到数据的，（个人：有时间就去学习逆向分析，但是真正实际爬的话，使用最低的成本获取最高的效益，**巧劲最大化**）

![在这里插入图片描述](https://img-blog.csdnimg.cn/2020070510284563.png)

> 题外话，虽然说一般的大厂不轻易封IP (误伤太大)，但如果真的想海量采集淘宝商品数据的话，还必须要花点时间研究反爬策略的，否则很难进行海量抓取，因为文章后面有可能是有点触发反爬了 （后面再说）

## 5. 抓包

如果在电脑首次上淘宝的话，你想搜索商品是必须要求登录的，淘宝的要求登录就不是我上一篇文章（撩妹佳文）那种假登录限制了，而且整体接口都要求**必须携带凭证（cookie）访问**的了，这里介绍一个巧劲，因为如果使用登录的 cookie 访问淘宝的话，淘宝实际上是知道你是谁的，有你访问的记录的，然后你上淘宝可能会给你推送商品的，甚至对你账号进行限制 、反爬等，因此写一个爬虫没必要搭上那么大的风险吧，所以**登录完成之后，就退出**，这个时候浏览器还是可以正常搜索产品的，意思就是说**不要使用登录的 cookie 进行爬取数据**。。。

> 五个抓包步骤如下

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705105653493.png)

> 观察 json 结构：复制上面的抓包的数据，然后进行 json 视图查看

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705110333523.png)

> 如何分页?

上面的抓包只是一页的数据，那么如何抓取淘宝搜索其他页呢？
我可以直接告诉你的是，通过在 url 传递一个 **s={最后一个商品的位置}** 实现的分页...

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705111245657.png)

好吧   我们来简单看一下吧：
* 在上面抓包中，我们并没有看到 url 传递了 **s** 这个参数
* 然后我们先来点击一下 "下一页",
多点几次观察几次，你就知道了 **s** 参数的变化了

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705112603226.png)

![在这里插入图片描述](https://img-blog.csdnimg.cn/2020070511305769.png)

> 观察 s 变化，第二页s=44，第三页s=88，第四页s=132，...

```
首页没有 s 参数
第二页：https://s.taobao.com/search?q=%E5%AE%89%E8%B8%8F%E7%AF%AE%E7%90%83%E9%9E%8B%E7%94%B7%E9%9E%8B&imgfile=&js=1&stats_click=search_radio_all%3A1&initiative_id=staobaoz_20200705&ie=utf8&bcoffset=3&ntoffset=3&p4ppushleft=1%2C48&s=44
第三页：https://s.taobao.com/search?q=%E5%AE%89%E8%B8%8F%E7%AF%AE%E7%90%83%E9%9E%8B%E7%94%B7%E9%9E%8B&imgfile=&js=1&stats_click=search_radio_all%3A1&initiative_id=staobaoz_20200705&ie=utf8&bcoffset=0&ntoffset=6&p4ppushleft=1%2C48&s=88
第三页：https://s.taobao.com/search?q=%E5%AE%89%E8%B8%8F%E7%AF%AE%E7%90%83%E9%9E%8B%E7%94%B7%E9%9E%8B&imgfile=&js=1&stats_click=search_radio_all%3A1&initiative_id=staobaoz_20200705&ie=utf8&bcoffset=0&ntoffset=6&p4ppushleft=1%2C48&s=132
```

来到这里，可以去掉其他不必要的参数，结论是：https://s.taobao.com/search?q=**{url encode keywords}**&s=**{开始位置}**

## 6. 编码

经过上面繁琐的步骤，终于可以着手编码了，因为爬取数据之后需要进行可视化，所以数据肯定是需要持久化的，为了方便就保存到 csv 文件中。

> 逗号分隔值（Comma-Separated Values，CSV，有时也称为字符分隔值，因为分隔字符也可以不是逗号），其文件以纯文本形式存储表格数据（数字和文本）。纯文本意味着该文件是一个字符序列，不含必须像二进制数字那样被解读的数据。


在代码里注释得很清楚的了，这里直接贴代码出来即可

> 为了简洁，我贴出主要的代码，想要获取整体源码项目的话，关注微信公告号（ it_loading 回复 "淘宝爬虫" 即可）

**FastHttpClient.java**

https://blog.csdn.net/JinglongSource/article/details/107136862

**TaoBaoSpider1.java**

```java
import cn.shaines.spider.util.FastHttpClient;
import cn.shaines.spider.util.FastHttpClient.Response;
import cn.shaines.spider.util.FastHttpClient.ResponseHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * @author for.houyu@qq.com
 */
@Slf4j
public class TaoBaoSpider1 {

    private static final String listUrlTemplate = "https://s.taobao.com/search?q=${keywords}&s=${index}";
    private static String cookie = "enc=l5xju4OJMzTwwtBkVJWbvFPcQ%2B6n6%2FWdRaE4iECxQOQiEtA45RBOyXQu0gZbOcIVEO6oxrRWvtu6mgSv4JZa8w%3D%3D; thw=cn; hng=CN%7Czh-CN%7CCNY%7C156; sgcookie=ESMDjIxrMIKwi48qmS8xP; tfstk=cCOGBNOa3dW_xoWHFf16ovEkfpHRZKNVqIRBTCGS8wNPBsdFiO2UUAat-GxMM-1..; tracknick=; cna=8XsbF0X0cVkCAbcg0Pt/qWLy; t=bb72251ba9e04aa4ffe5119a746b1f35; v=0; cookie2=1d43fd087e0cf8b4268f2e8ddcd4aea0; _tb_token_=e30835115bdfe; alitrackid=www.taobao.com; lastalitrackid=www.taobao.com; JSESSIONID=07CB217DBB9367EF2E0CCF7AF29AA9A0; isg=BDo6WaueMbQrb7zN4rxs3mhmi2Bc677FZ6MxoUQ_y0y9N99xLH6N1UYBh8PrpzZd; l=eBMkD1V4QZHXKy_vBO5whurza77ONdAfCsPzaNbMiInca6ZFN1uJuNQqG5uBldtjgtfj7etrb3kJjRUpziUdg2HvCbKrCyCk6Yp6-";

    private FastHttpClient httpClient;
    private BufferedWriter writer;
    private Set<String> filter;

    public static void main(String[] args) throws Exception {
        // 搜索关键字
        final String keywords = "安踏篮球鞋男鞋";
        // 每页44条数据
        final int limit = 44;
        TaoBaoSpider1 spider = new TaoBaoSpider1();
        spider.init(keywords);
        for (int page = 0; page <= 99; page++) {
            log.info("正在准备下载页数: {}", page + 1);
            String html = spider.getListHtml(keywords, page * limit);
            List<Goods> list  = spider.parse(html);
            log.info("解析得到数量: [{}]", list.size());
            if (list.isEmpty()) {
                break;
            }
            list = spider.doFilter(list);
            log.info("过滤后数量: [{}]", list.size());
            list.forEach(v -> {
                List<String> row = Arrays.asList(v.getRaw_title(), v.getView_price(), v.getView_fee(), v.getNick(), v.getItem_loc(), v.getView_sales(), v.getPic_url(), v.getDetail_url(), v.getComment_url(), v.getShopLink(), "_" + v.getNid(), "_" + v.getPid());
                spider.writeRow(row);
            });
            // 睡眠3 ~ 10秒
            Thread.sleep(ThreadLocalRandom.current().nextLong(3000, 10000));
            log.info("\r\n");
        }
    }

    private List<Goods> doFilter(List<Goods> list) {
        list = list.stream().filter(v -> !filter.contains(v.getNid())).collect(Collectors.toList());
        filter.addAll(list.stream().map(Goods::getNid).collect(Collectors.toSet()));
        return list;
    }

    /**
     * 写入一行数据
     * @param row 一行数据
     */
    protected void writeRow(List<String> row) {
        // 写入一行数据, csv的一行格式为,分割的, 但是这里使用","分割,主要就是为了统一作为字符串
        // 如:
        //      "姓名","年龄"
        //      "张三","123"
        String line = row.stream().map(v -> v.replace("\"", "\"\"").replace(",", ",,")).collect(Collectors.joining("\",\"", "\"", "\""));
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解析html
     * @param html the html
     */
    protected List<Goods> parse(String html) {
        if (StringUtils.isEmpty(html) || (html.contains("登录页面") && html.contains("全登陆不允许iframe嵌入"))) {
            throw new RuntimeException("获取列表HTML失败,请检查,如更新cookie等...");
        }
        String script = Arrays.stream(StringUtils.substringsBetween(html, "<script", "</script>"))
                // 过滤包含 g_page_config 和 auctions 的 script 脚本
                .filter(v -> v.contains("g_page_config") && v.contains("itemlist"))
                // 获取第一个符合条件的脚本(原则来上说这里只能返回一个, 否则说明上面的过滤不严谨)
                .findFirst()
                // 如果没有匹配的就抛异常, 说明解析页面失败
                .orElseThrow(() -> new RuntimeException("解析页面失败,请检查更新代码"));
        // 观察 script 内部其实是一个json格式的字符串, 因此找到分割点进行切割字符串返回一个json串
        String json = StringUtils.substringBetween(script, "g_page_config", "g_srp_init");
        json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
        // 使用 JSONPath 实现快速解析json, 这里的意思是查找n级 itemlist 下的 n级auctions (说明:alibaba fastjson 就有 JSONPath 的实现)
        Object eval = JSONPath.eval(JSON.parseObject(json), "$..itemlist..auctions");
        if (!(eval instanceof List)) {
            throw new RuntimeException("解析JSON列表失败, 请检查更新代码");
        }
        List<JSONObject> auctions = (List<JSONObject>) eval;
        // 转换为目标对象 Goods
        List<Goods> result = auctions.stream().map(v -> v.toJavaObject(Goods.class)).collect(Collectors.toList());
        // result.forEach(System.out::println);
        return result;
    }

    /**
     * 根据关键字获取列表HTML
     * @param keywords 关键字 如: 安踏篮球鞋男鞋
     * @param index 开始位置, 如0, 每页显示44条数据, 因此0, 44, 88, ...
     */
    protected String getListHtml(String keywords, int index) {
        // 中文进行URL编码
        keywords = FastHttpClient.encodeURLText(keywords, StandardCharsets.UTF_8);
        String url = listUrlTemplate.replace("${keywords}", keywords).replace("${index}", index + "");
        // 创建一个GET请求
        HttpGet httpGet = httpClient.buildGet(url);
        // 执行请求, 获取响应
        Response<String> response = httpClient.execute(httpGet, ResponseHandler.ofString());
        return response.getData();
    }

    /**
     * 资源初始化
     */
    protected void init(String keywords) {
        // 初始化 httpClient 并且设置 cookie 每次请求都会携带cookie信息
        httpClient = FastHttpClient.builder().setCookie(cookie).build();
        filter = new HashSet<>(1024);
        try {
            // 初始化 CSV 文件呢
            File file = Paths.get(System.getProperty("user.dir"), "temp", "spider", keywords + ".csv").toFile();
            file.getParentFile().mkdirs();
            writer = new BufferedWriter(new FileWriter(file, false));
            // 准备header
            List<String> header = Arrays.asList("标题", "单价", "运费", "店名", "发货地址", "售量", "首页图", "明细地址", "评论地址", "购买地址", "nid", "pid");
            this.writeRow(header);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static class Goods {
        /** nid */
        private String nid;
        /** pid */
        private String pid;
        /** 标题 */
        private String raw_title;
        /** 首页图 */
        private String pic_url;
        /** 明细地址 */
        private String detail_url;
        /** 单价 */
        private String view_price;
        /** 运费 */
        private String view_fee;
        /** 发货地址 */
        private String item_loc;
        /** 售量 */
        private String view_sales;
        /** 店名 */
        private String nick;
        /** 评论地址 */
        private String comment_url;
        /** 购买地址 */
        private String shopLink;

        public String getNid() {
            return nid;
        }

        public void setNid(String nid) {
            this.nid = nid;
        }

        public String getPid() {
            return pid;
        }

        public void setPid(String pid) {
            this.pid = pid;
        }

        public String getRaw_title() {
            return raw_title;
        }

        public void setRaw_title(String raw_title) {
            this.raw_title = raw_title;
        }

        public String getPic_url() {
            return pic_url;
        }

        public void setPic_url(String pic_url) {
            this.pic_url = pic_url;
        }

        public String getDetail_url() {
            return detail_url;
        }

        public void setDetail_url(String detail_url) {
            this.detail_url = detail_url;
        }

        public String getView_price() {
            return view_price;
        }

        public void setView_price(String view_price) {
            this.view_price = view_price;
        }

        public String getView_fee() {
            return view_fee;
        }

        public void setView_fee(String view_fee) {
            this.view_fee = view_fee;
        }

        public String getItem_loc() {
            return item_loc;
        }

        public void setItem_loc(String item_loc) {
            this.item_loc = item_loc;
        }

        public String getView_sales() {
            return view_sales;
        }

        public void setView_sales(String view_sales) {
            this.view_sales = view_sales;
        }

        public String getNick() {
            return nick;
        }

        public void setNick(String nick) {
            this.nick = nick;
        }

        public String getComment_url() {
            return comment_url;
        }

        public void setComment_url(String comment_url) {
            this.comment_url = comment_url;
        }

        public String getShopLink() {
            return shopLink;
        }

        public void setShopLink(String shopLink) {
            this.shopLink = shopLink;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Goods{");
            sb.append("nid='").append(nid).append('\'');
            sb.append(", pid='").append(pid).append('\'');
            sb.append(", raw_title='").append(raw_title).append('\'');
            sb.append(", pic_url='").append(pic_url).append('\'');
            sb.append(", detail_url='").append(detail_url).append('\'');
            sb.append(", view_price='").append(view_price).append('\'');
            sb.append(", view_fee='").append(view_fee).append('\'');
            sb.append(", item_loc='").append(item_loc).append('\'');
            sb.append(", view_sales='").append(view_sales).append('\'');
            sb.append(", nick='").append(nick).append('\'');
            sb.append(", comment_url='").append(comment_url).append('\'');
            sb.append(", shopLink='").append(shopLink).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

}

```

## 7. 运行代码

> 效果如下

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705125932626.png)

文件保存路径默认在项目路径 $/temp/spider/xxx.csv

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705171040360.png)

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705130142799.png)

一共爬了2074条数据，爬到大概50页的时候，发现返回的数据都是重复的了（根据nid过滤）
大概猜测原因：
* nid 字段不具备唯一性过滤
* 淘宝反爬
* 淘宝限制返回数量（虽然写着99页，实际真实客户也不可能是真的翻了99页找商品...）
* ...

## 8. 数据可视化

以下可视化数据仅供学习参考，不具备任何依据判断，不承担任何责任。

**1. 淘宝 - 安踏篮球鞋男鞋 - 发货地词云图**

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705174131722.png)

**2. 淘宝 - 安踏篮球鞋男鞋 - 店铺售量冲浪榜**

> 仅展示店铺售量1900+以上店铺，不上榜的店铺纳入 “其他”

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705130846186.gif)

**3. 淘宝 - 安踏篮球鞋男鞋 - 价格&售量表1**

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705131021620.gif)

**4. 淘宝 - 安踏篮球鞋男鞋 - 价格&售量表2**

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705172212938.gif)

## 9. 扩展

上面的代码完成一个关键字的搜索，顶多可以算一个爬虫，并不能算一个完整的爬虫系统，一个爬虫系统应该具备以下的几个组件。（待完善）

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200705163245132.png)

---

获取源码，请关注我的公众号 ：IT加载中（it_loading）
回复 "淘宝爬虫"即可

---

公众号：IT加载中（it_loading）
CSDN：https://blog.csdn.net/JinglongSource
博客：https://shaines.cn/
邮箱：for.houyu@qq.com

程序员 [ 后宇 ]，是一个关注编程，热爱技术的Java后端开发者，热衷于 [ Java后端 ]，[ 数据爬虫领域 ]。不定期分享 I T 技能和干货！！欢迎关注 “IT加载中”，一个只出 干货 和 实战 的公众号。

