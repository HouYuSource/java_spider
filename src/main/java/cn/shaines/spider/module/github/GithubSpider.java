package cn.shaines.spider.module.github;

import cn.shaines.ChromiumBrowser;

/**
 * @author houyu
 */
public class GithubSpider {

    public static void main(String[] args) {
        ChromiumBrowser browser = ChromiumBrowser.builder().build();
        browser.loadURL("https://github.com/login");
        // browser.until(v -> v.getContext().)
        browser.until(v -> !v.getURL().contains("login"), 1000 * 60 * 10);
        // System.out.println("browser.getCookie() = " + browser.getCookie());
        if (browser.getURL().contains("login")) {
            throw new RuntimeException("请在10分钟之内登录");
        }
        System.out.println("browser.getURL() = " + browser.getURL());

    }

}
