import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Random;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.openqa.selenium.support.ui.ExpectedConditions.*;

public class AukroTest {

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeClass
    public void setUp() throws MalformedURLException {
        //String seleniumServerAddress = "http://selenium:4444/wd/hub";
        String seleniumServerAddress = "http://127.0.0.1:4444/wd/hub";

        if (driver == null) {
            driver = new RemoteWebDriver(new URL(seleniumServerAddress), new ChromeOptions());
            driver.manage().window().maximize();
        }
        wait = new WebDriverWait(driver, 10);
        driver.get("https://aukro.cz");


        // accept data harvesting pop-up
        wait
                .pollingEvery(ofMillis(250))
                .withTimeout(ofSeconds(10))
                .until(
                        visibilityOfElementLocated(By.cssSelector(".fc-cta-consent"))
                ).click();

        // close cookie consent at the bottom of the page
        wait.until(elementToBeClickable(By.cssSelector(".cookiesWrap > .cookiesButton"))).click();

        // close email harvesting pop-up - seems it appears after ~ 30 seconds, timeout set to 60 to be on the safe side
        wait
                .pollingEvery(ofMillis(250))
                .withTimeout(ofSeconds(60))
                .until(
                        visibilityOfElementLocated(By.cssSelector("email-collector-popup.ng-star-inserted > .flex > a > .material-icons"))
                ).click();
    }


    @Test
    public void test() throws InterruptedException {

        List<WebElement> categories = driver.findElements(By.cssSelector("top-level-category"));

        String categoryName, categoryHref;
        for (WebElement category : categories) {

            categoryName = category.findElement(By.cssSelector("a")).getText();
            categoryHref = category.findElement(By.cssSelector("a")).getAttribute("href");

            wait.until(elementToBeClickable(category)).click();

            // synchronization & checks
            wait.until(urlToBe(categoryHref));
            wait.until(textToBePresentInElementLocated(By.cssSelector(".sidebar-title"), categoryName));

            // filter offers with 'Garance vrácení peněz`
            WebElement filterCheckbox = wait.until(visibilityOfElementLocated(By.cssSelector("#mat-checkbox-6")));

            Actions builder = new Actions(driver);
            builder.moveToElement(filterCheckbox).click(filterCheckbox);
            builder.perform();

            // synchronization & checks
            wait.until(attributeContains(filterCheckbox, "class", "mat-checkbox-checked"));
            Thread.sleep(1500); // yeah, an anti-pattern
            builder.moveToElement(driver.findElement(By.cssSelector(".settings-wrapper .active-filters div"))).perform();
            wait.until(
                    textToBePresentInElementLocated(By.cssSelector(".settings-wrapper .active-filters div"), "Aukro garance vrácení peněz" ));
            wait.until(
                    urlContains("paymentViaAukro=true"));

            // check there are at least 5 offers
            int filteredOffersCount = Integer.parseInt(wait
                    .until(visibilityOfElementLocated(
                            By.cssSelector(".details > span"))).getText().replace(" ", ""));

            if (filteredOffersCount >= 5) {
                List<WebElement> offers = wait.until(
                        visibilityOfAllElementsLocatedBy(By.cssSelector("list-view > list-card")));

                // assert softly that each displayed offer (60 per page) has the badge
                SoftAssert soft = new SoftAssert();
                boolean isMoneyGuaranteeBadgeDisplayed;

                for (WebElement offer : offers) {
                    builder.moveToElement(offer).perform();
                    isMoneyGuaranteeBadgeDisplayed = offer.findElement(By.xpath("//span[contains(text(), 'Aukro Garance vrácení peněz')]")).isDisplayed();
                    soft.assertTrue(isMoneyGuaranteeBadgeDisplayed, "Item does not have money guaranteed back badge.");
                }
                soft.assertAll();

                // selecting offer to open in detail
                int offerIndexToOpen = offers.size() % 2 == 0 ? (int) ((Math.random() * (offers.size()))) : offers.size() / 2;

                WebElement offerToOpen = offers.get(offerIndexToOpen);
                builder.moveToElement(offerToOpen).perform();
                wait.until(elementToBeClickable(offerToOpen.findElement(By.cssSelector(".box-wrapper > a")))).click();

                // verify detail (badge next to name)
                boolean isBadgeNextNameVisible = wait.until(visibilityOfElementLocated(By.cssSelector("heading svg-icon#money-back-guarantee"))).isDisplayed();

                // verify detail (badge next to delivery options)
                boolean isBadgeNextDeliveryOptionsVisible = wait.until(visibilityOfElementLocated(By.cssSelector("delivery-info ul svg-icon#money-back-guarantee"))).isDisplayed();

                Assert.assertTrue(isBadgeNextNameVisible, "Missing 'money back guarantee' badge next to name in detail page");
                Assert.assertTrue(isBadgeNextDeliveryOptionsVisible, "Missing 'money back guarantee' badge next to delivery options in detail page");


                // detecting bid
                boolean isBid, isBuy;
                try {
                    wait.withTimeout(ofSeconds(5)).until(visibilityOfElementLocated(By.xpath("//button[contains(text(), 'Přihodit')]")));
                    isBid = true;
                } catch (TimeoutException e) {
                    isBid = false;
                }

                // detecting buy
                try {
                    wait.withTimeout(ofSeconds(5)).until(visibilityOfElementLocated(By.xpath("//button[contains(text(), 'Přidat do košíku')]")));
                    isBuy = true;
                } catch (TimeoutException e) {
                    isBuy = false;
                }

                if (isBid && !isBuy) {
                    bidAndVerify();
                }

                if (!isBid && isBuy) {
                    buyAndVerify();
                }

                if (isBid && isBuy) {
                    boolean toss = new Random().nextBoolean();
                    if (toss) {
                        bidAndVerify();
                    } else {
                        buyAndVerify();
                    }
                }

            break;
            }

            // category had less than 5 items - return back from the category and loop over a next category
            wait.until(elementToBeClickable(By.cssSelector("categories > h2 > a"))).click();
            wait.until(
                    textToBePresentInElementLocated(
                            By.cssSelector("button.top-level-categories-cta"), "Vyberte si kategorii"));
        }
    }

   public void bidAndVerify() {
       // do bid + 20 %
       int currentMaxBid = Integer.parseInt(wait.until(
               visibilityOfElementLocated(
                       By.cssSelector(".price")))
               .getText().replace(" ", "").replace("Kč", ""));

       WebElement priceInput = wait.until(visibilityOfElementLocated(By.cssSelector("price-input input")));
       priceInput.clear();
       priceInput.sendKeys(String.valueOf(currentMaxBid * 1.2));

       // click bid button and verify login page is shown
       wait.until(visibilityOfElementLocated(By.xpath("//button[contains(text(), 'Přihodit')]"))).click();
       wait.until(titleIs("Přihlášení na Aukru"));
       wait.until(textToBePresentInElementLocated(By.cssSelector(".introduction"), "Vítejte na Aukru!"));
   }

   public void buyAndVerify() {
       // do buy
       String offerName, offerPrice;
       offerName = wait.until(visibilityOfElementLocated(By.cssSelector("heading > h1"))).getText().strip();
       offerPrice = wait.until(visibilityOfElementLocated(By.cssSelector(".price"))).getText();

       wait.until(visibilityOfElementLocated(By.xpath("//button[contains(text(), 'Přidat do košíku')]"))).click();

       // open basket and verify the item is there
       wait.until(visibilityOfElementLocated(By.cssSelector("basket-control"))).click();
       wait.until(urlContains("/kosik"));
       wait.until(textToBePresentInElementLocated(By.cssSelector("seller-cart-items a"), offerName));
       wait.until(textToBePresentInElementLocated(By.cssSelector(".price-section > p"), offerPrice));
   }


    @AfterClass
    public void tearDown() {
        if (driver != null) {
            driver.close();
            driver.quit();
        }
    }
}
