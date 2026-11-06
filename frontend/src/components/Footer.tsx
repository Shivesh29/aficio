import { Disc3 } from 'lucide-react';

export const Footer = () => {
  return (
    <footer className="site-footer">
      <div className="footer-container">
        <div className="footer-brand-col">
          <div className="footer-logo">
            <Disc3 size={18} />
            <span>aficio</span>
          </div>
          <p className="footer-motto">
            Track, rate, and discover records you love.
          </p>
        </div>

        <div className="footer-links-group">
          <div className="footer-col">
            <h4>Explore</h4>
            <a href="/">Catalog</a>
            <a href="/lists">Collections</a>
            <a href="/search">Discover & Import</a>
          </div>

          <div className="footer-col">
            <h4>Community</h4>
            <a href="/feed">Feed</a>
            <a href="/lists">Collections</a>
          </div>
        </div>
      </div>

      <div className="footer-bottom">
        <span>© {new Date().getFullYear()} Aficio — Music as a shared memory.</span>
      </div>
    </footer>
  );
};
