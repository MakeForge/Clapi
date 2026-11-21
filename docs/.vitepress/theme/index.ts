import DefaultTheme from 'vitepress/theme';
import ThemedScreenshot from './components/ThemedScreenshot.vue';
import HeroProtocolIcons from './components/HeroProtocolIcons.vue';
import './custom.css';
import { h } from 'vue';
import { VPButton } from 'vitepress/theme';

export default {
  extends: DefaultTheme,
  enhanceApp({app, router}) {
    app.component('ThemedScreenshot', ThemedScreenshot);

    if (typeof window !== 'undefined' && !(window as any).__clapi_nav_listener_registered) {
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
                  size: 'medium'  // 'medium' | 'big'
                })
              ]
          )
    })
  }
};
