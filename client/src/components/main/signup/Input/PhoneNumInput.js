import { useState } from "react";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import Grid from "@mui/material/Grid";
import customAxios from "utils/customAxios";

// recoil import
import { useRecoilState, useSetRecoilState } from "recoil";
import { phoneCheckState, phoneValidState, phoneDataState } from "stores/SignUpStates";

function PhoneNumInput({ color }) {
    // state 선언
    const [phoneNum, setPhoneNum] = useState("");
    // 전화번호 인증 확인 변수
    const [isValid, setIsValid] = useRecoilState(phoneCheckState);
    // 전체 인증 결과 확인 변수
    const [phoneValid, setPhoneValid] = useRecoilState(phoneValidState);
    // 인증 번호 변수
    const [authNum, setAuthNum] = useState(null);
    // 인증 번호 입력 변수
    const [authInputNum, setAuthInputNum] = useState(null);
    // 최종 전화번호 저장 변수
    const setPhoneData = useSetRecoilState(phoneDataState);

    const axios = customAxios();

    // 전화번호 데이터 저장
    const handlePhoneNum = e => {
        const value = e.target.value;
        setPhoneNum(value);
        setIsValid("notChecked");
        setPhoneValid("notChecked");
    };

    // 인증번호 입력값 얻기
    const getAuthInputNum = e => {
        const value = e.target.value;
        setAuthInputNum(value);
        setIsValid("notChecked");
        setPhoneValid("notChecked");
    };

    // 전화번호 인증 요청 함수
    const getAuthNum = async () => {
        const phoneNum = await axios.get("/v2/user/check/phonenum");
        alert(phoneNum.data.body);
        setAuthNum(phoneNum);
    };

    // 전화번호 인증 함수
    const checkAuthNum = () => {
        axios
            .post(`/v2/user/verify/phonenum`, { authnum: authInputNum })
            .then(res => {
                if (res.data.state) {
                    // 전화번호 인증 확인
                    setIsValid("valid");
                    // 유효성 검사 요청
                    setPhoneValid("notChecked");
                    // 최종 데이터 저장
                    setPhoneData(phoneNum);
                } else {
                    setIsValid("notValid");
                    console.log("no");
                    console.log(authInputNum);
                }
            })
            .catch(error => console.log(error));
    };

    return (
        <Grid container item xs={12} spacing={2}>
            <Grid item xs={9}>
                <TextField
                    required
                    fullWidth
                    id="phoneNum"
                    label="전화번호(-없이)"
                    name="phoneNum"
                    autoComplete="phoneNum"
                    color={color}
                    onChange={handlePhoneNum}
                    disabled={isValid === "valid" ? true : false}
                />
            </Grid>
            <Grid item xs={3}>
                <Button
                    variant="contained"
                    size="medium"
                    sx={{ padding: 1, height: 55, color: "#ffffff", fontSize: 16 }}
                    fullWidth
                    color={color}
                    disabled={phoneNum.length !== 11 ? true : false}
                    onClick={getAuthNum}
                >
                    인증요청
                </Button>
            </Grid>
            <Grid item xs={12}>
                <TextField
                    required
                    fullWidth
                    name="authNum"
                    label="인증번호"
                    type="authNum"
                    id="authNum"
                    color={color}
                    error={(isValid !== "notValid") & (phoneValid !== "notValid") ? false : true}
                    helperText={
                        isValid === "notValid"
                            ? "올바르지 못한 인증번호입니다"
                            : isValid === "valid"
                            ? "인증되었습니다"
                            : phoneValid === "notValid"
                            ? "전화번호를 인증해주세요"
                            : null
                    }
                    onChange={getAuthInputNum}
                    disabled={isValid === "valid" ? true : false}
                />
            </Grid>
            <Grid item xs={12}>
                <Button
                    fullWidth
                    variant="contained"
                    sx={{
                        height: 55,
                        color: "#ffffff",
                        fontWeight: "bold",
                        fontSize: 20,
                        marginBottom: 5,
                    }}
                    color="custom"
                    onClick={checkAuthNum}
                    disabled={authNum === null ? true : false}
                >
                    인증확인
                </Button>
            </Grid>
        </Grid>
    );
}

export default PhoneNumInput;
