/** Runtime configuration. No secrets ever belong here: everything in this file ships to the browser. */
export const environment = {
  /**
   * All calls go to the Spring Boot backend (the dev server proxies /api to it, see proxy.conf.json).
   * The private ML service is never called from the browser.
   */
  apiBaseUrl: '/api',
};
