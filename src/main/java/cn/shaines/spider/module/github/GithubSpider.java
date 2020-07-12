package cn.shaines.spider.module.github;

import cn.shaines.ChromiumBrowser;
import com.teamdev.jxbrowser.chromium.dom.By;
import com.teamdev.jxbrowser.chromium.dom.DOMElement;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * @author houyu
 */
@Slf4j
public class GithubSpider {

    private static final String loginUrl = "https://github.com/login";
    private static final String repositoriesUrlTemplate = "https://github.com/${username}?tab=repositories";
    private static final String gitUrlTemplate = "https://github.com${repositories}.git";
    private static final String gitCommandTemplate = "git clone ${repositories} ${path}";


    public static void main(String[] args) {
        // 创建浏览器实例
        ChromiumBrowser browser = ChromiumBrowser.builder().build();
        // 处理登录
        handleLogin(browser);
        // 获取用户名
        String username = browser.findElement(By.cssSelector("details > summary span.css-truncate.css-truncate-target")).getInnerText();
        // 查找需要下载的git url
        List<String> gitUrls = findGitUrls(browser, username);
        // 设置并发量下载数量为3
        Semaphore semaphore = new Semaphore(3);
        for (int i = 0, size = gitUrls.size(); i < size; i++) {
            // if (i > 0) {
            //     break;
            // }
            String repositoriesUrl = gitUrls.get(i);
            log.info("正在开启任务: [{}]: [{}]", i, repositoriesUrl);
            execTask(semaphore, repositoriesUrl, username);
            log.info("完成任务: [{}]: [{}]", i, repositoriesUrl);
        }
        System.exit(1);
    }

    /**
     * 获取所有git下载的url
     * @param browser 浏览器实例
     * @param username 用户名
     */
    private static List<String> findGitUrls(ChromiumBrowser browser, String username) {
        String url = repositoriesUrlTemplate.replace("${username}", username);
        browser.loadURL(url);
        browser.await();
        browser.until(v -> v.findElements(By.cssSelector("#user-repositories-list li h3 > a")).size() > 0, 1000 * 60 * 10);
        List<DOMElement> repositoriesList = browser.findElements(By.cssSelector("#user-repositories-list li h3 > a"));
        List<String> repositories = repositoriesList.stream().map(v -> v.getAttribute("href")).collect(Collectors.toList());
        // https://github.com/HouYuSource/java_spider.git
        return repositories.stream().map(v -> gitUrlTemplate.replace("${repositories}", v)).collect(Collectors.toList());
    }

    /**
     * 处理登录
     * @param browser 浏览器实例
     */
    private static void handleLogin(ChromiumBrowser browser) {
        browser.loadURL(loginUrl);
        browser.until(v -> !v.getURL().contains("login"), 1000 * 60 * 10);
        if (browser.getURL().contains("login")) {
            throw new RuntimeException("请在10分钟之内登录");
        }
    }

    /**
     * 执行下载任务
     * @param semaphore 信号量
     * @param repositoriesUrl 资源库Url
     * @param username 用户名
     */
    public static void execTask(Semaphore semaphore, String repositoriesUrl, String username) {
        Path basePath = Paths.get(System.getProperty("user.dir"), "temp", "github_backups");
        String repositoriesName = StringUtils.substringBetween(repositoriesUrl, username + "/", ".git");
        String path = Paths.get(basePath.toString(), repositoriesName).toString();
        String command = gitCommandTemplate.replace("${repositories}", repositoriesUrl).replace("${path}", path);
        Runtime runtime = Runtime.getRuntime();
        try {
            // 开始消费信号(这个acquire()方法, 会尝试去获取往下执行的信号, 如果获取不到,一直在等待, 直到其他信号释放释放)
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        new Thread(() -> {
            try {
                Process exec = runtime.exec(command);
                IOUtils.toString(exec.getInputStream());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // 释放信号
                semaphore.release();
            }
        }).start();
    }

}
