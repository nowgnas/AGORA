import { useResetRecoilState } from "recoil";

import { userInfoState } from "stores/atoms";

function Logout() {
    const resetUserInfo = useResetRecoilState(userInfoState);

    const logout = () => {
        resetUserInfo();
        window.location.href = '/';
    }

    return (
        <div>
            <button onClick={logout}>
                ๋ก๊ทธ์์
            </button>
        </div>
    )
}

export default Logout;