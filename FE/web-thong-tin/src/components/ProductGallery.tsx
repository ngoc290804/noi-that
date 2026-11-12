import { useEffect, useId, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import type { ProductImage } from '../types/catalog';
import { Icon } from './Icon';
import { Photo } from './Photo';

export function ProductGallery({ images, name }: { images: readonly ProductImage[]; name: string }) {
  const [selectedIndex, setSelectedIndex] = useState(0);
  const thumbnails = useRef<HTMLDivElement>(null);
  const imageId = useId();
  const helpId = useId();
  const index = Math.max(0, Math.min(selectedIndex, images.length - 1));
  const currentImage = images[index];
  const hasMultiple = images.length > 1;

  // Scroll only the thumbnail strip, never move the page when an arrow is clicked.
  useEffect(() => {
    const strip = thumbnails.current;
    const selected = strip?.querySelector<HTMLButtonElement>('[aria-pressed="true"]');
    if (!strip || !selected) return;
    const stripRect = strip.getBoundingClientRect();
    const selectedRect = selected.getBoundingClientRect();
    if (selectedRect.left < stripRect.left) strip.scrollTo({ left: strip.scrollLeft + selectedRect.left - stripRect.left });
    else if (selectedRect.right > stripRect.right) strip.scrollTo({ left: strip.scrollLeft + selectedRect.right - stripRect.right });
  }, [index, images.length]);

  function move(direction: number) {
    setSelectedIndex((previous) => (Math.min(previous, images.length - 1) + direction + images.length) % images.length);
  }

  function onKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (!hasMultiple || event.target !== event.currentTarget || event.altKey || event.ctrlKey || event.metaKey) return;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault();
      move(event.key === 'ArrowLeft' ? -1 : 1);
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault();
      setSelectedIndex(event.key === 'Home' ? 0 : images.length - 1);
    }
  }

  return (
    <section className="product-gallery" aria-label={`Ảnh sản phẩm ${name}`} aria-roledescription={hasMultiple ? 'bộ ảnh' : undefined} aria-describedby={hasMultiple ? helpId : undefined} tabIndex={hasMultiple ? 0 : undefined} onKeyDown={onKeyDown}>
      {hasMultiple ? <p id={helpId} className="sr-only">Dùng phím mũi tên trái và phải để chuyển ảnh; Home và End để chọn ảnh đầu và cuối.</p> : null}
      <div className="gallery-stage" id={imageId}>
        {currentImage ? <Photo key={currentImage.src} image={currentImage} priority sizes="(min-width: 900px) 55vw, 100vw" /> : <div className="image-fallback" role="img" aria-label={`Ảnh ${name} đang cập nhật`}>Ảnh đang được cập nhật</div>}
        {hasMultiple ? <>
          <button className="gallery-arrow gallery-arrow-previous" type="button" aria-label="Ảnh trước" aria-controls={imageId} onClick={() => move(-1)}><Icon name="arrow-left" /></button>
          <button className="gallery-arrow gallery-arrow-next" type="button" aria-label="Ảnh tiếp theo" aria-controls={imageId} onClick={() => move(1)}><Icon name="arrow-right" /></button>
        </> : null}
      </div>
      {hasMultiple ? <div ref={thumbnails} className="gallery-thumbnails" role="group" aria-label="Chọn ảnh sản phẩm">{images.map((image, imageIndex) => (
        <button className="gallery-thumbnail" type="button" key={`${image.src}-${imageIndex}`} aria-label={`Xem ảnh ${imageIndex + 1}: ${image.alt}`} aria-pressed={index === imageIndex} aria-controls={imageId} onClick={() => setSelectedIndex(imageIndex)}>
          <Photo image={{ ...image, alt: '' }} sizes="96px" />
        </button>
      ))}</div> : null}
      {hasMultiple ? <p className="sr-only" role="status" aria-live="polite" aria-atomic="true">Ảnh {index + 1} / {images.length}</p> : null}
    </section>
  );
}
