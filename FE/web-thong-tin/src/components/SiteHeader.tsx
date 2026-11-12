import { useEffect, useRef, useState } from 'react';
import type { FormEvent, MouseEvent } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { Icon } from './Icon';

function dismissBackdrop(event: MouseEvent<HTMLDialogElement>) {
  if (event.target === event.currentTarget) event.currentTarget.close();
}

export function SiteHeader() {
  const location = useLocation();
  const navigate = useNavigate();
  const menu = useRef<HTMLDialogElement>(null);
  const search = useRef<HTMLDialogElement>(null);
  const searchInput = useRef<HTMLInputElement>(null);
  const [searchValue, setSearchValue] = useState('');

  useEffect(() => {
    menu.current?.close();
    search.current?.close();
  }, [location]);

  function openSearch() {
    setSearchValue(new URLSearchParams(location.search).get('q') ?? '');
    search.current?.showModal();
    searchInput.current?.focus();
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (searchValue.trim()) params.set('q', searchValue.trim());
    navigate(`/san-pham${params.size ? `?${params}` : ''}`);
    search.current?.close();
  }

  return (
    <>
      <header className="site-header">
        <div className="container header-inner">
          <button className="icon-button mobile-menu-toggle" onClick={() => menu.current?.showModal()} aria-label="Mở menu" aria-haspopup="dialog"><Icon name="menu" /></button>
          <Link to="/" className="brand" aria-label="Luma — Trang chủ">Luma</Link>
          <nav className="desktop-nav" aria-label="Điều hướng chính">
            <NavLink to="/" end>Trang chủ</NavLink>
            <NavLink to="/san-pham">Sản phẩm</NavLink>
            <Link to="/#ve-luma">Về Luma</Link>
          </nav>
          <button className="header-search" onClick={openSearch} aria-haspopup="dialog" aria-label="Mở tìm kiếm"><span>Tìm kiếm</span><Icon name="search" /></button>
        </div>
      </header>

      <dialog className="menu-dialog" ref={menu} aria-labelledby="menu-title" onClick={dismissBackdrop}>
        <div className="menu-content">
          <div className="dialog-header"><span className="brand" id="menu-title">Luma</span><button className="icon-button" aria-label="Đóng menu" onClick={() => menu.current?.close()}><Icon name="close" /></button></div>
          <nav aria-label="Điều hướng di động"><Link to="/">Trang chủ <Icon name="arrow-right" /></Link><Link to="/san-pham">Sản phẩm <Icon name="arrow-right" /></Link><Link to="/#ve-luma">Về Luma <Icon name="arrow-right" /></Link></nav>
          <p className="menu-note">Những món đồ nhỏ.<br />Một tổ ấm của riêng bạn.</p>
        </div>
      </dialog>

      <dialog className="search-dialog" ref={search} aria-labelledby="search-title" onClick={dismissBackdrop}>
        <div className="search-content">
          <div className="dialog-header"><h2 id="search-title">Bạn đang tìm món đồ nào?</h2><button className="icon-button" aria-label="Đóng tìm kiếm" onClick={() => search.current?.close()}><Icon name="close" /></button></div>
          <form className="search-form" role="search" onSubmit={submitSearch}>
            <label className="sr-only" htmlFor="global-search">Tên sản phẩm</label>
            <input ref={searchInput} id="global-search" type="search" value={searchValue} onChange={(event) => setSearchValue(event.target.value)} maxLength={120} placeholder="Thử tìm “ghế ăn” hoặc “gốm”…" />
            <button className="icon-button" type="submit" aria-label="Tìm sản phẩm"><Icon name="arrow-right" /></button>
          </form>
          <p className="muted">Một chiếc ghế, một chiếc bình… khám phá món đồ cho góc nhà của bạn.</p>
        </div>
      </dialog>
    </>
  );
}
