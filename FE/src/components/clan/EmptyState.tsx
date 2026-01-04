import { Plus, Search } from 'lucide-react';

export function EmptyState({
  onCreate,
  onSearch,
}: {
  onCreate: () => void;
  onSearch: () => void;
}) {
  return (
    <div className="flex flex-col items-center justify-center pb-30">
      <p className="text-gray-500 text-lg mb-10">You are not in an clan:</p>

      <section className="flex flex-col justify-center items-center gap-2">
        <span className="inline-flex items-center gap-2 rounded-full px-4 text-gray-700 text-2xl font-semibold">
          ✉️ Get invited to an Clan
        </span>

        <div className="relative my-6 w-full max-w-xs">
          {' '}
          <div
            className="absolute inset-0 flex items-center"
            aria-hidden="true"
          >
            {' '}
            <div className="w-full border-t border-[#D9D9D9]" />{' '}
          </div>{' '}
          <div className="relative flex justify-center">
            {' '}
            <span className="bg-white px-3 text-gray-700">OR</span>{' '}
          </div>{' '}
        </div>
        <div className="flex flex-col gap-2">
          <button
            className="inline-flex justify-center items-center gap-2 rounded-full bg-[#D9D9D9] px-7 py-3 font-semibold text-2xl text-gray-700 hover:opacity-90 cursor-pointer"
            onClick={onCreate}
          >
            <Plus />
            <span className="pb-1.5">Create an clan</span>
          </button>

          <button
            className="inline-flex justify-center items-center gap-2 rounded-full bg-[#D9D9D9] px-7 py-3 font-semibold text-2xl text-gray-700 hover:opacity-90 cursor-pointer"
            onClick={onSearch}
          >
            <Search />
            <span className="pb-1.5">Search clans</span>
          </button>
        </div>
      </section>
    </div>
  );
}
