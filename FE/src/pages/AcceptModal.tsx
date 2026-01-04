import { useNavigate } from 'react-router-dom';

type Props = { fullPage?: boolean };

export default function AcceptModal({ fullPage = false }: Props) {
  const navigate = useNavigate();
  const onClose = () => navigate(-1);

  return (
    <div
      className={
        fullPage
          ? 'fixed inset-0 bg-white' // 전체 페이지로 사용될 때
          : 'fixed inset-0 bg-black/40 flex items-center justify-center' // 오버레이
      }
      role="dialog"
      aria-modal="true"
    >
      <div className="bg-white rounded-xl p-6 shadow-xl w-[560px] max-w-[90vw]">
        <h2 className="text-xl font-semibold mb-4">Accept</h2>
        <p className="text-sm mb-6">최초 로그인 동의 내용…</p>
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-2 rounded bg-gray-100">
            Close
          </button>
          <button className="px-3 py-2 rounded bg-blue-600 text-white">
            Agree
          </button>
        </div>
      </div>
    </div>
  );
}
