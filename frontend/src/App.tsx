
import React from "react";

const App: React.FC = () => {
  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="ECHO home">
          <span className="brand-mark">E</span>
          <span>ECHO</span>
        </a>

        <span className="topbar-label">YOUR MUSIC SPACE</span>
      </header>

      <section className="welcome-section">
        <p className="eyebrow">YOUR SOUND. YOUR WORLD.</p>
        <h1>
          Music for
          <br />
          every <span>moment.</span>
        </h1>
        <p className="welcome-copy">
          Find your rhythm, discover new sounds, and make every
          moment yours.
        </p>
      </section>

      <section className="featured-section" aria-labelledby="featured-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">A NEW WAY TO LISTEN</p>
            <h2 id="featured-title">Your next favorite starts here.</h2>
          </div>
          <span className="featured-badge">ECHO DISCOVERY</span>
        </div>

        <div className="discovery-panel">
          <div className="sound-art" aria-hidden="true">
            <span className="sound-orb sound-orb-one" />
            <span className="sound-orb sound-orb-two" />
            <span className="sound-orb sound-orb-three" />
            <span className="sound-letter">E</span>
          </div>

          <div className="discovery-copy">
            <p className="eyebrow">MADE FOR YOUR NEXT CHAPTER</p>
            <h3>There is a sound for every side of you.</h3>
            <p>
              Your music journey begins with exploration.
              The right soundtrack is waiting to be discovered.
            </p>
            <span className="coming-soon">MUSIC DISCOVERY COMING SOON</span>
          </div>
        </div>
      </section>

      <footer className="app-footer">
        <span>ECHO</span>
        <span>Engaging Collaborative Hub for Online Music</span>
      </footer>
    </main>
  );
};

export default App;
