import { useEffect, useRef } from 'react';
import type { FormEvent, MouseEvent } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { Icon } from './Icon';
import { BrandLogo } from './BrandLogo';

function dismissBackdrop(event: MouseEvent<HTMLDialogElement>) {
  if (event.target === event.currentTarget) event.currentTarget.close();
}

export function SiteHeader() {
  const location = useLocation();
  const navigate = useNavigate();
  const menu = useRef<HTMLDialogElement>(null);
  const searchValue = new URLSearchParams(location.search).get('q') ?? '';

  useEffect(() => {
    menu.current?.close();
  }, [location]);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = String(new FormData(event.currentTarget).get('q') ?? '').trim();
    const params = new URLSearchParams();
    if (value) params.set('q', value);
    const query = params.toString();
    navigate(`/san-pham${query ? `?${query}` : ''}`);
  }

  return (
    <>
      <header className="site-header">
        <div className="container header-inner">
          <button className="icon-button mobile-menu-toggle" onClick={() => menu.current?.showModal()} aria-label="Mở menu" aria-haspopup="dialog"><Icon name="menu" /></button>
          <Link to="/" className="brand" aria-label="Luma — Trang chủ"><BrandLogo /></Link>
          <nav className="desktop-nav" aria-label="Điều hướng chính">
            <NavLink to="/" end>Trang chủ</NavLink>
            <NavLink to="/san-pham">Sản phẩm</NavLink>
            <Link to="/#ve-luma">Về Luma</Link>
          </nav>
          <form key={location.search} className="header-search-form search-form" role="search" aria-label="Tìm kiếm trên menu" onSubmit={submitSearch}>
            <label className="sr-only" htmlFor="header-search">Tìm sản phẩm trên menu</label>
            <input id="header-search" name="q" type="search" defaultValue={searchValue} maxLength={120} placeholder="Tìm sản phẩm…" onChange={(event) => {
              if (event.currentTarget.value === '' && location.pathname === '/san-pham' && location.search) navigate('/san-pham');
            }} />
            <button className="icon-button" type="submit" aria-label="Tìm kiếm sản phẩm"><Icon name="search" /></button>
          </form>
        </div>
      </header>

      <dialog className="menu-dialog" ref={menu} aria-labelledby="menu-title" onClick={dismissBackdrop}>
        <div className="menu-content">
          <div className="dialog-header"><span className="brand" id="menu-title"><BrandLogo /></span><button className="icon-button" aria-label="Đóng menu" onClick={() => menu.current?.close()}><Icon name="close" /></button></div>
          <nav aria-label="Điều hướng di động"><Link to="/">Trang chủ <Icon name="arrow-right" /></Link><Link to="/san-pham">Sản phẩm <Icon name="arrow-right" /></Link><Link to="/#ve-luma">Về Luma <Icon name="arrow-right" /></Link></nav>
          <p className="menu-note">Những món đồ nhỏ.<br />Một tổ ấm của riêng bạn.</p>
        </div>
      </dialog>
    </>
  );
}
