package com.agent4j.bilibili.web;

public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String error;

    /**
     * 构造统一的接口响应对象。
     *
     * @param success 是否成功
     * @param data 响应数据
     * @param error 错误信息
     */
    private ApiResponse(boolean success, T data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    /**
     * 创建成功响应。
     *
     * @param data 响应数据
     * @return 包含数据的成功结果
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 创建失败响应。
     *
     * @param error 错误信息
     * @return 包含错误描述的失败结果
     */
    public static <T> ApiResponse<T> failure(String error) {
        return new ApiResponse<>(false, null, error);
    }

    /**
     * 判断当前响应是否成功。
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取响应数据。
     *
     * @return 数据内容
     */
    public T getData() {
        return data;
    }

    /**
     * 获取错误信息。
     *
     * @return 错误描述
     */
    public String getError() {
        return error;
    }
}
