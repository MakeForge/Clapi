<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { withBase } from 'vitepress';

defineProps<{
  light: string;
  dark: string;
  alt?: string;
}>();

const isLightboxOpen = ref(false);
const isMounted = ref(false);

onMounted(() => {
  isMounted.value = true;
  window.addEventListener('keydown', onKeyDown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown);
  if (typeof document !== 'undefined') {
    document.body.style.overflow = '';
  }
});

function openLightbox() {
  isLightboxOpen.value = true;
  if (typeof document !== 'undefined') {
    document.body.style.overflow = 'hidden';
  }
}

function closeLightbox() {
  isLightboxOpen.value = false;
  if (typeof document !== 'undefined') {
    document.body.style.overflow = '';
  }
}

function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isLightboxOpen.value) {
    closeLightbox();
  }
}
</script>

<template>
  <div class="themed-screenshot-container">
    <div
      class="themed-screenshot-frame"
      @click="openLightbox"
      title="Click to expand high-resolution preview"
    >
      <!-- Screenshot images (Focused Workbench View) -->
      <div class="screenshot-viewport">
        <img
          :src="withBase(light)"
          :alt="alt || ''"
          class="screenshot-img screenshot-light"
          loading="lazy"
        />
        <img
          :src="withBase(dark)"
          :alt="alt || ''"
          class="screenshot-img screenshot-dark"
          loading="lazy"
        />
      </div>

      <!-- Action Toolbar with Expand button -->
      <div class="screenshot-toolbar" @click.stop>
        <button
          type="button"
          class="toolbar-btn"
          @click="openLightbox"
          title="Expand high-resolution screenshot"
          aria-label="Expand high-resolution screenshot"
        >
          <svg class="btn-icon" viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
            <path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z" />
          </svg>
          <span class="toolbar-label">Expand</span>
        </button>
      </div>
    </div>

    <!-- Lightbox Modal -->
    <Teleport to="body" v-if="isMounted && isLightboxOpen">
      <div class="screenshot-lightbox" @click="closeLightbox" role="dialog" aria-modal="true">
        <div class="lightbox-backdrop"></div>
        <div class="lightbox-content" @click.stop>
          <div class="lightbox-header">
            <span class="lightbox-title">{{ alt || 'Clapi Workbench Preview' }}</span>
            <button
              type="button"
              class="lightbox-close-btn"
              @click="closeLightbox"
              aria-label="Close lightbox"
            >
              ✕
            </button>
          </div>
          <div class="lightbox-image-container">
            <img
              :src="withBase(light)"
              :alt="alt || ''"
              class="lightbox-img screenshot-light"
            />
            <img
              :src="withBase(dark)"
              :alt="alt || ''"
              class="lightbox-img screenshot-dark"
            />
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.themed-screenshot-container {
  margin: 1rem 0 1.25rem 0;
  width: 100%;
}

.themed-screenshot-frame {
  position: relative;
  border-radius: 8px;
  border: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  cursor: zoom-in;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

:root.dark .themed-screenshot-frame,
.dark .themed-screenshot-frame {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.45);
}

.themed-screenshot-frame:hover {
  border-color: var(--vp-c-brand-1);
}

.screenshot-viewport {
  position: relative;
  width: 100%;
  aspect-ratio: 1340 / 800;
  overflow: hidden;
  display: block;
  background-color: var(--vp-c-bg-soft);
}

.screenshot-img {
  width: 119.4% !important;
  max-width: none !important;
  transform: translateX(-184px); /* Closer to 0% = shows more of the left */
  display: block;
  margin: 0 !important;
  border-radius: 0 !important;
  border: none !important;
  box-shadow: none !important;
}

/* Toolbar overlay */
.screenshot-toolbar {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 6px;
  z-index: 10;
  background: var(--vp-c-bg-soft);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--vp-c-divider);
  border-radius: 6px;
  padding: 3px;
  opacity: 0.88;
  transition: opacity 0.2s ease, background-color 0.2s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.themed-screenshot-frame:hover .screenshot-toolbar {
  opacity: 1;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  background: transparent;
  color: var(--vp-c-text-2);
  border: none;
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.toolbar-btn:hover {
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1);
}

.btn-icon {
  display: inline-block;
}

/* Lightbox Modal */
.screenshot-lightbox {
  position: fixed;
  inset: 0;
  z-index: 99999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}

.lightbox-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.82);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.lightbox-content {
  position: relative;
  z-index: 100000;
  max-width: 95vw;
  max-height: 92vh;
  display: flex;
  flex-direction: column;
  background: var(--vp-c-bg);
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.6);
  overflow: hidden;
}

.lightbox-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.6rem 1rem;
  border-bottom: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
}

.lightbox-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--vp-c-text-1);
}

.lightbox-close-btn {
  background: transparent;
  border: none;
  font-size: 1.1rem;
  line-height: 1;
  color: var(--vp-c-text-2);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.lightbox-close-btn:hover {
  background: var(--vp-c-bg-mute);
  color: var(--vp-c-text-1);
}

.lightbox-image-container {
  overflow: auto;
  padding: 0.75rem;
  display: flex;
  align-items: center;
  justify-content: center;
  max-height: calc(92vh - 54px);
  background: var(--vp-c-bg-alt);
}

.lightbox-img {
  max-width: 100%;
  max-height: calc(92vh - 80px);
  object-fit: contain;
  border-radius: 6px;
  display: block;
}
</style>
