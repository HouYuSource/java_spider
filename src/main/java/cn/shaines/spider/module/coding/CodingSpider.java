package cn.shaines.spider.module.coding;

import cn.shaines.ChromiumBrowser;

/**
 * @author houyu
 */
public class CodingSpider {

    public static void main(String[] args) {
        ChromiumBrowser browser = ChromiumBrowser.builder().build();
        browser.loadURL("https://e.coding.net/login");
        // browser.until(v -> v.getContext().)
        browser.until(v -> !v.getURL().contains("login"), 1000 * 60 * 5);
        System.out.println("browser.getCookie() = " + browser.getCookie());
    }

}
