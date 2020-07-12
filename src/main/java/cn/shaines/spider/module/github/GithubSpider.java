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
 * github备份
 * @author houyu
 */
@Slf4j
public class GithubSpider {

    /** github 登录地址 */
    private static final String loginUrl = "https://github.com/login";
    /** github 用户的仓库地址模板 */
    private static final String repositoriesUrlTemplate = "https://github.com/${username}?tab=repositories";
    /** git 具体的资源库模板 */
    private static final String gitUrlTemplate = "https://github.com${repositories}.git";
    /** git 命令模板 */
    private static final String gitCommandTemplate = "git clone ${repositories} ${path}";

    public static void main(String[] args) throws Exception {
        // 创建浏览器实例
        ChromiumBrowser browser = ChromiumBrowser.builder().build();
        // 处理登录
        handleLogin(browser);
        browser.until(v -> v.findElement(By.cssSelector("a.user-profile-link strong")) != null, 1000 * 60 * 10);
        // 获取用户名
        String username = browser.findElement(By.cssSelector("a.user-profile-link strong")).getInnerText();
        // 查找需要下载的git url
        List<String> gitUrls = findGitUrls(browser, username);
        System.out.println("gitUrls = " + gitUrls);
        // 设置并发量下载数量为3
        Semaphore semaphore = new Semaphore(3);
        for (int i = 0, size = gitUrls.size(); i < size; i++) {
            // if (i > 0) {
            //     // 测试一个
            //     break;
            // }
            String repositoriesUrl = gitUrls.get(i);
            System.out.println("正在开启任务: [" + (i + 1) + "]: [" + repositoriesUrl + "]");
            execTask(semaphore, repositoriesUrl, username);
            System.out.println("完成任务: [" + (i + 1) + "]: [" + repositoriesUrl + "]");
        }
        System.out.println("完成任务");
        Thread.sleep(1000);
        System.exit(-1);
    }

    /**
     * 获取所有git下载的url
     * @param browser 浏览器实例
     * @param username 用户名
     */
    private static List<String> findGitUrls(ChromiumBrowser browser, String username) {
        String url = repositoriesUrlTemplate.replace("${username}", username);
        browser.loadURL(url);
        // 根据css 选择器查找元素, 最长等待10分钟(考虑网络不好的情况), 找到了立刻往下执行流程
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
        // 可以替换为任意目录下
        // Path basePath = Paths.get("C:\\Users\\houyu\\Desktop\\temp2");
        String repositoriesName = StringUtils.substringBetween(repositoriesUrl, username + "/", ".git");
        String path = Paths.get(basePath.toString(), repositoriesName).toString();
        String command = gitCommandTemplate.replace("${repositories}", repositoriesUrl).replace("${path}", path);
        Runtime runtime = Runtime.getRuntime();
        try {
            // 开始消费(这个acquire()方法, 会尝试去获取往下执行的信号, 如果获取不到,一直在等待, 直到其他信号释放释放)
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        new Thread(() -> {
            try {
                Process exec = runtime.exec(command);
                // 下面这行代码会一直撞阻塞到下载完成
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
