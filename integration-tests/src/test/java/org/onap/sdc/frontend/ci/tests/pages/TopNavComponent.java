/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2019 Nordix Foundation
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 *  ============LICENSE_END=========================================================
 */

package org.onap.sdc.frontend.ci.tests.pages;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.onap.sdc.frontend.ci.tests.execute.setup.DriverFactory;
import org.onap.sdc.frontend.ci.tests.pages.home.HomePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the Top Navigation Component UI actions
 */
public class TopNavComponent extends AbstractPageObject {

    private static final Logger LOGGER = LoggerFactory.getLogger(TopNavComponent.class);

    private WebElement wrappingElement;
    private By navLocator = By.xpath(XpathSelector.NAV.getXpath());

    public TopNavComponent(final WebDriver webDriver) {
        super(webDriver);
        timeoutInSeconds = 5;
    }

    @Override
    public void isLoaded() {
        wrappingElement = getWrappingElement();
    }

    /**
     * Gets the enclosing element of the component.
     *
     * @return the enclosing element
     */
    public WebElement getWrappingElement() {
        LOGGER.debug("Finding element with xpath '{}'", XpathSelector.NAV.getXpath());
        return waitForElementVisibility(navLocator);
    }

    /**
     * Clicks on home link inside the first breadcrumb arrow.
     */
    public HomePage clickOnHome() {
        hoverToBreadcrumbArrow(0);
        final By homeButtonLocator = By.xpath(XpathSelector.SUB_MENU_BUTTON_HOME.getXpath());
        waitForElementVisibility(homeButtonLocator);
        waitToBeClickable(homeButtonLocator).click();
        return new HomePage(webDriver, this);
    }

    public boolean isHomeSelected() {
        final By homeLinkLocator = By.xpath(XpathSelector.MAIN_MENU_LINK_HOME.getXpath());
        final WebElement homeLinkElement = waitToBeClickable(homeLinkLocator);
        final WebElement homeLinkParentElement = homeLinkElement.findElement(By.xpath("./.."));
        final String homeLinkClass = homeLinkParentElement.getAttribute("class");
        LOGGER.debug(String.format("Home link class '%s'", homeLinkClass));
        return homeLinkClass != null && homeLinkClass.contains("selected");
    }

    /**
     * Click on one of the items of the top nav breadcrumb based on the given position.
     * The first item in the breadcrumb is position 0.
     *
     * @param position the position of the breadcrumb item
     */
    public void clickOnBreadCrumb(final int position) {
        if (position < 0) {
            throw new IllegalStateException("The position cannot be less that zero");
        }
        waitForElementVisibility(By.xpath(String.format("//*[@data-tests-id='breadcrumbs-button-%s']", position))).click();
    }

    public void waitRepositoryToBeClickable() {
        waitToBeClickable(XpathSelector.REPOSITORY_ICON.getXpath());
    }

    /**
     * Clicks on the VSP repository icon.
     *
     * @return the next page object
     */
    public VspRepositoryModalComponent clickOnRepositoryIcon() {
        wrappingElement.findElement(By.xpath(XpathSelector.REPOSITORY_ICON.getXpath())).click();

        return new VspRepositoryModalComponent(webDriver);
    }

    /**
     * Clicks on the Onboard button.
     *
     * @return the next page object
     */
    public OnboardHomePage clickOnOnboard() {
        waitForElementInvisibility(By.xpath(XpathSelector.SDC_LOADER_BACKGROUND.getXpath()));
        wrappingElement.findElement(By.xpath(XpathSelector.MAIN_MENU_ONBOARD_BTN.getXpath())).click();
        return new OnboardHomePage(DriverFactory.getDriver(), new OnboardHeaderComponent(DriverFactory.getDriver()));
    }

    /**
     * Hover to a breadcrumb arrow of the given position.
     *
     * @param arrowPosition the position of the arrow from left to right
     * @return the hovered breadcrumb arrow element
     */
    public WebElement hoverToBreadcrumbArrow(final int arrowPosition) {
        final Actions actions = new Actions(webDriver);
        final List<WebElement> arrowElementList = getWait()
            .until(
                ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath(XpathSelector.ARROW_DROPDOWN.getXpath())));
        final WebElement selectedArrowElement = arrowElementList.get(arrowPosition);
        actions.moveByOffset(20, 20).moveToElement(selectedArrowElement).perform();
        return selectedArrowElement;
    }

    /**
     * Enum that contains identifiers and xpath expressions to elements related to the enclosing page object.
     */
    @AllArgsConstructor
    private enum XpathSelector {
        NAV("top-nav", "//nav[@class='%s']"),
        SUB_MENU_BUTTON_HOME("sub-menu-button-home", "//*[@data-tests-id='%s']"),
        MAIN_MENU_LINK_HOME("main-menu-button-home", "//*[@data-tests-id='%s']"),
        ARROW_DROPDOWN("triangle-dropdown", "//li[contains(@class, '%s')]"),
        MAIN_MENU_ONBOARD_BTN("main-menu-button-onboard", "//a[@data-tests-id='%s']"),
        REPOSITORY_ICON("repository-icon", "//*[@data-tests-id='%s']"),
        SDC_LOADER_BACKGROUND("sdc-loader-global-wrapper sdc-loader-background", "//div[@class='%s']");

        @Getter
        private final String id;
        private final String xpathFormat;

        public String getXpath() {
            return String.format(xpathFormat, id);
        }
    }

}
