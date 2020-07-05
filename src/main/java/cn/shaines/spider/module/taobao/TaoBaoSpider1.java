package cn.shaines.spider.module.taobao;

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
