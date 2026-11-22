import DefaultTheme, {VPButton} from 'vitepress/theme';
import {h} from 'vue';
import ThemedScreenshot from './components/ThemedScreenshot.vue';
import HeroProtocolIcons from './components/HeroProtocolIcons.vue';
import './custom.css';

function scrollActiveSidebarItem(behavior: ScrollBehavior = 'smooth') {
  if (typeof window === 'undefined') return;

  // Delay slightly so VitePress finishes expanding collapsible groups and updating .is-active
  setTimeout(() => {
    const sidebar = document.querySelector<HTMLElement>('.VPSidebar');
    const activeLink = document.querySelector<HTMLElement>(
        '.VPSidebar .VPSidebarItem.is-active > .item .link'
    );

    if (!sidebar || !activeLink) return;

    const sidebarRect = sidebar.getBoundingClientRect();
    const linkRect = activeLink.getBoundingClientRect();

    // Only scroll if the active item is near/outside the visible sidebar area
    const isOutOfView =
        linkRect.top < sidebarRect.top + 80 ||
        linkRect.bottom > sidebarRect.bottom - 40;

    if (isOutOfView) {
      activeLink.scrollIntoView({
        behavior,
        block: 'center',
      });
    }
  }, 120);
}

export default {
  extends: DefaultTheme,
  enhanceApp({app, router}) {
    app.component('ThemedScreenshot', ThemedScreenshot);

    if (typeof window !== 'undefined') {
      // Scroll active sidebar item into view on initial page load
      scrollActiveSidebarItem('instant');

      // Scroll active sidebar item into view whenever the user navigates to another subpage
      const prevAfterRouteChange = router.onAfterRouteChange;
      router.onAfterRouteChange = (to) => {
        prevAfterRouteChange?.(to);
        scrollActiveSidebarItem('smooth');
      };

      if (!(window as any).__clapi_nav_listener_registered) {
        (window as any).__clapi_nav_listener_registered = true;

        window.addEventListener(
            'click',
            (e) => {
              const target = e.target as HTMLElement | null;
              if (!target) return;

              // When pressing a navigation bar group topic (e.g. Getting Started, Request Types)
              const navGroupBtn = target.closest('.VPNavBarMenuGroup > .button');
              if (!navGroupBtn) return;

              const groupEl = navGroupBtn.closest('.VPNavBarMenuGroup');
              if (!groupEl) return;

              // Find the first link belonging to that group in the flyout menu
              const firstLink = groupEl.querySelector<HTMLAnchorElement>('.menu a[href]');
              if (!firstLink) return;

              const url = firstLink.getAttribute('href');
              if (!url) return;

              if (e.metaKey || e.ctrlKey || e.shiftKey) {
                window.open(url, '_blank');
                return;
              }

              e.preventDefault();
              e.stopPropagation();

              (navGroupBtn as HTMLElement).blur();
              router.go(url);
            },
            true
        );
      }
    }
  },
  Layout() {
    return h(DefaultTheme.Layout, null, {
      'home-hero-info-after': () => h(HeroProtocolIcons),
      // Places the button right after the top nav links
      'nav-bar-content-before': () =>
          h(
              'div',
              { class: 'nav-search-cta' },
              [
                h(VPButton, {
                  text: 'Download',
                  href: 'https://github.com/MakeForge/Clapi/releases/latest',
                  theme: 'brand', // 'brand' | 'alt' | 'sponsor'
                  size: 'medium', // 'medium' | 'big'
                }),
              ]
          ),
    });
  },
};