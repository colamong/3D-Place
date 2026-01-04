import React from 'react';
import { useLocation } from 'react-router-dom';
import ModalShell from '@/components/common/ModalShell';
import earth3D from '@/assets/earth3D.jpg';
import { BookOpenCheck } from 'lucide-react';
import RuleCard from '@/components/info/RuleCard';

type Props = {};

const InfoModal = (props: Props) => {
  return (
    <ModalShell>
      <div className="flex flex-col gap-2 h-full w-full">
        {/* 헤더 */}
        <header className="flex flex-col gap-4 justify-center">
          <div className="flex justify-center">
            <img
              src={earth3D}
              alt="3D Earth"
              className="w-24 max-w-2xl rounded-2xl aspect-[16/9] object-cover"
              loading="lazy"
              decoding="async"
              draggable={false}
            />
            <p className="text-xl md:text-5xl font-extrabold">3D PLACE</p>
          </div>
          <section className="flex w-full justify-center items-center">
            <p>Map powered by: CesiumJS&copy;</p>
          </section>
        </header>

        {/* 본문 */}
        <main className="flex flex-col gap-4 overflow-y-auto">
          {/* How to paint faster */}
          <div className="flex flex-col gap-1">
            <div className="flex">
              <span className="text-xl md:text-2xl font-semibold">
                Shortcut guide
              </span>
            </div>
            <span>Press Q to start painting voxels on the map.</span>
            <span>Press S to select voxels to edit or erase.</span>
            <span>Press C to choose any color you want.</span>
            <span>Press T to show or hide the control panels.</span>
          </div>

          {/* Rules */}
          <div className="flex flex-col gap-2 justify-center">
            <div className="flex items-center gap-2">
              <BookOpenCheck />
              <span className="text-xl md:text-3xl font-semibold">Rules</span>
              <span className="flex rounded-2xl bg-red-50 text-red-400 px-3 items-center font-semibold h-7">
                important
              </span>
            </div>
            <section className="flex flex-col gap-3">
              <RuleCard icon="😈">
                Do not paint over other artworks using random colors or patterns
                just to mess things up
              </RuleCard>

              <RuleCard icon="🚫" intent="warn">
                No inappropriate content (+18, hate speech, inappropriate links,
                highly suggestive material, ...)
              </RuleCard>

              <RuleCard icon="🧑‍🤝‍🧑">
                Do not paint with more than one account
              </RuleCard>

              <RuleCard icon="🤖">Use of bots is not allowed</RuleCard>

              <RuleCard icon="🙅">
                Disclosing other's personal information is not allowed
              </RuleCard>

              <RuleCard icon="✅">
                Painting over other artworks to complement them or create a new
                drawing is allowed
              </RuleCard>

              <RuleCard icon="✅">
                Griefing political party flags or portraits of politicians is
                allowed
              </RuleCard>
            </section>
          </div>
        </main>
        <footer className="flex w-full justify-center items-center text-gray-400">
          Email: contact@3dplace.kr · Terms · Privacy · Refund · Ban appeal ·
          Suggestions · Bug report
        </footer>
      </div>
    </ModalShell>
  );
};

export default InfoModal;
