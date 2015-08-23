package com.example.snake;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import java.util.ArrayList;
import java.util.List;

public class Pager extends View {
    private int mWidth = 1280;
    private int mHeight = 768;
    private List<ImagePiece> curImagePieces;
    private boolean isSplitCurImage = true;
    private List<ImagePiece> nextImagePieces;
    private boolean isSplitNextImage = true;
    private int mRows = 4;
    private int mColumns = 8;
    private float[] oldCoordX;
    private float[] oldCoordY;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private float rotateX = 30.0f;
    private float deltaDistance;
    private boolean isSetCoord = false;
    //是否优先画当前页
    private boolean isCurrentFirst = false;
    //拖拽点
    private PointF mTouch = new PointF();
    //第一次拖拽的点
    private PointF mFirstTouch = new PointF();

    private Bitmap mCurPageBitmap = null;
    private Bitmap mNextPageBitmap = null;

    private Scroller mScroller;
    private Matrix mMatrix;

    private float distance;
    private float offsetUpDistance;
    private float offsetUpHeight;
    private float offsetDownDistance;
    private float offsetDownHeight;

    private Bitmap bitmap1;
    private Bitmap bitmap2;
    private Bitmap bitmap3;
    private Paint mPaint;

    public Pager(Context context, int screenWidth, int screenHeight) {
        super(context);
        this.mWidth = screenWidth;
        this.mHeight = screenHeight;
        mScroller = new Scroller(getContext());
        mMatrix = new Matrix();

        bitmap1 = BitmapFactory.decodeResource(getResources(), R.drawable.gl_effect_snake_background);
        bitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.gl_effect_snake_backlight);
        bitmap3 = BitmapFactory.decodeResource(getResources(), R.drawable.gl_effect_snake_bottom_light);

        mPaint = new Paint();
        mPaint.setDither(true);
        mPaint.setAntiAlias(true);

        setWillNotDraw(false);
    }

    /**
     * bitmap单位
     */
    private class ImagePiece {
        public int index = 0;
        public Bitmap bitmap = null;
        //-1 : 未设置  0 : 从右往左滑动  1 : 从左往右滑动
        public int direction = -1;
    }

    /**
     * 切割bitmap
     *
     * @param bitmap
     * @param row
     * @param column
     * @return
     */
    private List<ImagePiece> split(Bitmap bitmap, int row, int column) {
        List<ImagePiece> pieces = new ArrayList<ImagePiece>(row * column);

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int pieceWidth = width / column;
        int pieceHeight = height / row;

        for (int i = 0; i < row; i++) {
            for (int j = 0; j < column; j++) {
                ImagePiece imagePiece = new ImagePiece();
                imagePiece.index = j + i * column;

                int xValue = j * pieceWidth;
                int yValue = i * pieceHeight;

                imagePiece.bitmap = Bitmap.createBitmap(bitmap, xValue, yValue,
                        pieceWidth, pieceHeight);
                pieces.add(imagePiece);
            }
        }
        return pieces;
    }

    /**
     * 初期化数据
     */
    private void initData() {
        oldCoordX = new float[mRows * mColumns];
        oldCoordY = new float[mRows * mColumns];
        /**
         * 计算每个bitmap的移动距离
         */
        distance = getMeasuredWidth() / mColumns * (mColumns - 1) * mRows + getMeasuredHeight();
        if (mRows % 2 == 1) {
            offsetUpDistance = getMeasuredWidth() / mColumns * (mColumns - 1) + +getMeasuredHeight() / mRows;
            offsetUpHeight = getMeasuredHeight() + getMeasuredHeight() / mRows;
            offsetDownDistance = getMeasuredWidth() / mColumns * (mColumns - 1) + getMeasuredHeight() / mRows;
            offsetDownHeight = -getMeasuredHeight() - getMeasuredHeight() / mRows;
        } else {
            offsetUpDistance = 0;
            offsetUpHeight = getMeasuredHeight();
            offsetDownDistance = 0;
            offsetDownHeight = -getMeasuredHeight();
        }

        /**
         * 保证每个bitmap是个正方形，避免bitmap产生重叠（不好看）
         * 计算每个bitmap的宽或高应该缩小多少
         */
        if (getMeasuredHeight() / mRows > getMeasuredWidth() / mColumns) {
            scaleX = 0.9f;
            scaleY = ((float) getMeasuredWidth() / mColumns) / ((float) getMeasuredHeight() / mRows) * 0.9f;
        } else {
            scaleX = ((float) getMeasuredHeight() / mRows) / ((float) getMeasuredWidth() / mColumns) * 0.9f;
            scaleY = 0.9f;
        }


        /**
         * 计算bitmap现在所处的坐标
         */
        if (curImagePieces != null && curImagePieces.size() > 0) {
            for (int i = 0; i < mRows; i++) {
                for (int j = 0; j < mColumns; j++) {
                    oldCoordX[i * mColumns + j] = getPieceBitmapX(i, j);
                    oldCoordY[i * mColumns + j] = getPieceBitmapY(i, j);
                }
            }
        }
    }

    private float getPieceBitmapX(int row, int col) {
        float x = 0.0f;
        for (int i = 0; i < col; i++) {
            x += curImagePieces.get(row * mColumns + i).bitmap.getWidth();
        }
        return x;
    }

    private float getPieceBitmapY(int row, int col) {
        float y = 0.0f;
        for (int i = 0; i < row; i++) {
            y += curImagePieces.get(i * mColumns + col).bitmap.getHeight();
        }
        return y;
    }

    /**
     * 绘制移动的部分
     *
     * @param canvas       画布
     * @param imagePiece   bitmap（被绘制的对象）
     * @param curDistance  当前离初始位置的距离
     * @param oldCoordX    初始位置（x坐标）
     * @param oldCoordY    初始位置（y坐标）
     * @param offsetHeight 画布偏移高度
     * @param uod          1：代表手势从左往右，下一页从下面往上出现   -1：代表手势从右往左，下一页从上面往下出现
     */
    private void drawSnake(Canvas canvas, ImagePiece imagePiece, float curDistance, float oldCoordX, float oldCoordY, float offsetHeight, int uod) {
        //当前x坐标
        float coordX = 0;
        //当前y坐标
        float coordY = 0;

        /**
         * direction为0代表bitmap的移动方向为从右向左
         * direction为1代表bitmap的移动方向为从左向右
         */
        if (imagePiece.direction == 0) {
            //滑离初始行后的距离
            float curDistance1 = curDistance - oldCoordX - imagePiece.bitmap.getHeight();
            //每一行的滑动距离（不包括初始行）
            float curWidth = imagePiece.bitmap.getWidth() * (mColumns - 1) + imagePiece.bitmap.getHeight();
            //offset小于零：还处在初始行 offset大于零：已经滑离初始行
            float offset = curDistance1 / curWidth;
            //当前bitmap划过了完整的一行的行数
            //完整的一行的距离为curWidth
            int intOffset = (int) Math.floor(offset);

            if (offset <= 0) {
                if (oldCoordX - curDistance >= 0) { //还在初始行
                    coordX = oldCoordX - curDistance;
                    coordY = oldCoordY;
                } else {    //在行的拐角处
                    coordX = 0;
                    coordY = oldCoordY + uod * (curDistance - oldCoordX);
                }
            } else {
                if (intOffset % 2 == 0) { //当前bitmap正在从左往右滑动
                    if ((curWidth * offset - intOffset * curWidth) > imagePiece.bitmap.getWidth() * (mColumns - 1)) { //正处在右边拐角处
                        coordX = imagePiece.bitmap.getWidth() * (mColumns - 1);
                        coordY = oldCoordY + uod * (intOffset + 1) * imagePiece.bitmap.getHeight() + uod * ((curWidth * offset - intOffset * curWidth) - imagePiece.bitmap.getWidth() * (mColumns - 1));
                    } else {    //正处在当前行（非拐角处）
                        coordX = curWidth * offset - intOffset * curWidth;
                        coordY = oldCoordY + uod * (intOffset + 1) * imagePiece.bitmap.getHeight();
                    }
                } else {    //当前bitmap正在从右往左滑动
                    if ((curWidth * offset - intOffset * curWidth) > imagePiece.bitmap.getWidth() * (mColumns - 1)) { // 正处在左边拐角处
                        coordX = 0;
                        coordY = oldCoordY + uod * (intOffset + 1) * imagePiece.bitmap.getHeight() + uod * ((curWidth * offset - intOffset * curWidth) - imagePiece.bitmap.getWidth() * (mColumns - 1));
                    } else {    //正处在当前行（非拐角处）
                        coordX = imagePiece.bitmap.getWidth() * (mColumns - 1) - (curWidth * offset - intOffset * curWidth);
                        coordY = oldCoordY + uod * (intOffset + 1) * imagePiece.bitmap.getHeight();
                    }
                }
            }
        } else {
            //滑离初始行后的距离
            float curDistance1 = curDistance - (imagePiece.bitmap.getWidth() * (mColumns - 1) - oldCoordX) - imagePiece.bitmap.getHeight();
            //每一行的滑动距离（不包括初始行）
            float curWidth = imagePiece.bitmap.getWidth() * (mColumns - 1) + imagePiece.bitmap.getHeight();
            //offset小于零：还处在初始行 offset大于零：已经滑离初始行
            float offset = curDistance1 / curWidth;
            //当前bitmap划过了完整的一行的行数
            //完整的一行的距离为curWidth
            int intOffset = (int) Math.floor(offset);

            if (offset <= 0) {
                if (oldCoordX + curDistance <= imagePiece.bitmap.getWidth() * (mColumns - 1)) { //还在初始行
                    coordX = oldCoordX + curDistance;
                    coordY = oldCoordY;
                } else {    //在行的拐角处
                    coordX = imagePiece.bitmap.getWidth() * (mColumns - 1);
                    coordY = oldCoordY + uod * (curDistance - (imagePiece.bitmap.getWidth() * (mColumns - 1) - oldCoordX));
                }
            } else {
                if (intOffset % 2 == 0) {   //当前bitmap正在从右往左滑动
                    if ((curWidth * offset - intOffset * curWidth) > imagePiece.bitmap.getWidth() * (mColumns - 1)) {   //正处在左边拐角处
                        coordX = 0;
                        coordY = oldCoordY + uod * (intOffset + 1) * imagePiece.bitmap.getHeight() + uod * ((curWidth * offset - intOffset * curWidth) - imagePiece.bitmap.getWidth() * (mColumns - 1));
                    } else {    //正处在当前行（非拐角处）
                        coordX = imagePiece.bitmap.getWidth() * (mColumns - 1) - (curWidth * offset - intOffset * curWidth);
                        coordY = oldCoordY + uod * (intOffset + 1) * imagePiece.bitmap.getHeight();
                    }
                } else {    //当前bitmap正在从左往右滑动
                    if ((curWidth * offset - intOffset * curWidth) > imagePiece.bitmap.getWidth() * (mColumns - 1)) {   //正处在右边拐角处
                        coordX = imagePiece.bitmap.getWidth() * (mColumns - 1);
                        coordY = oldCoordY + uod * (intOffset + 1) * imagePiece.bitmap.getHeight() + uod * ((curWidth * offset - intOffset * curWidth) - imagePiece.bitmap.getWidth() * (mColumns - 1));
                    } else {    //正处在当前行（非拐角处）
                        coordX = curWidth * offset - intOffset * curWidth;
                        coordY = oldCoordY + uod * (intOffset + 1) * imagePiece.bitmap.getHeight();
                    }
                }
            }
        }

        mMatrix.reset();
        canvas.save();

        mMatrix.preScale(scaleX, scaleY);
        mMatrix.preTranslate(-(imagePiece.bitmap.getWidth() / 2), -(imagePiece.bitmap.getHeight() / 2));
        mMatrix.postTranslate(+(imagePiece.bitmap.getWidth() / 2), +(imagePiece.bitmap.getHeight() / 2));
        mMatrix.postTranslate(coordX, coordY + offsetHeight);
        canvas.drawBitmap(imagePiece.bitmap, mMatrix, null);

        canvas.restore();
    }

    /**
     * 根据滑动距离绘制玻璃
     *
     * @param canvas
     */
    private void drawGlass(Canvas canvas) {
        float ratio = Math.abs(deltaDistance) / (mWidth / 16);
        if (deltaDistance == 0) {
            return;
        }
        if (ratio <= 1.0f) {
            mPaint.setAlpha((int) (ratio * 255));
        } else {
            ratio = (Math.abs(deltaDistance) - mWidth / 2) / (mWidth / 4);
            if (ratio >= 0 && ratio <= 1.0f) {
                mPaint.setAlpha((int) ((1 - ratio) * 255));
            } else if (ratio < 0) {
                mPaint.setAlpha(255);
            } else {
                return;
            }
        }

        Rect mDestRect1 = new Rect(0, 0, mWidth, mHeight);
        NinePatch patch = new NinePatch(bitmap1, bitmap1.getNinePatchChunk(), null);
        patch.draw(canvas, mDestRect1, mPaint);

        Rect mSrcRect2 = new Rect(0, 0, bitmap2.getWidth(), bitmap2.getHeight());
        Rect mDestRect2 = new Rect(0, 0, mWidth, mHeight - bitmap3.getHeight());
        canvas.drawBitmap(bitmap2, mSrcRect2, mDestRect2, mPaint);

        Rect mSrcRect3 = new Rect(0, 0, bitmap3.getWidth(), bitmap3.getHeight());
        Rect mDestRect3 = new Rect(0, mHeight - bitmap3.getHeight(), mWidth / 2, mHeight);
        canvas.drawBitmap(bitmap3, mSrcRect3, mDestRect3, mPaint);
    }

    private void drawCurPageArea(Canvas canvas, Bitmap bitmap) {
        /**
         * 将图片切成mColumn*mColumn份
         */
        if (isSplitCurImage) {
            isSplitCurImage = false;
            curImagePieces = split(bitmap, mRows, mColumns);

            if (!isSetCoord) {
                initData();
                isSetCoord = true;
            }
        }

        if (deltaDistance <= 0) {
            float transformRatio = Math.abs(deltaDistance) / (mWidth / 16);

            if (transformRatio <= 1) {
                /*for(int i = 0;i < mRows;i++) {
                    for (int j = 0; j < mColumns; j++) {
                        ImagePiece imagePiece = curImagePieces.get(i * mColumns + j);
                        if (imagePiece.direction == -1) {
                            if ((mRows - i) % 2 == 1) {
                                imagePiece.direction = 0;
                            } else {
                                imagePiece.direction = 1;
                            }
                        }
                    }
                }*/
                for (int i = 0; i < mRows; i++) {
                    for (int j = 0; j < mColumns; j++) {
                        ImagePiece imagePiece = curImagePieces.get(i * mColumns + j);

                        mMatrix.reset();
                        canvas.save();

                        mMatrix.preScale(1 - (1 - scaleX) * transformRatio, 1 - (1 - scaleY) * transformRatio);
                        mMatrix.preTranslate(-(imagePiece.bitmap.getWidth() / 2), -(imagePiece.bitmap.getHeight() / 2));
                        mMatrix.postTranslate(+(imagePiece.bitmap.getWidth() / 2), +(imagePiece.bitmap.getHeight() / 2));
                        mMatrix.postTranslate(oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j]);
                        canvas.drawBitmap(imagePiece.bitmap, mMatrix, null);

                        canvas.restore();
                    }
                }
            } else {
                transformRatio = (Math.abs(deltaDistance) - mWidth / 16) / (mWidth / 2);
                transformRatio = transformRatio > 1.0f ? 1.0f : transformRatio;

                for (int i = mRows - 1; i >= 0; i--) {
                    for (int j = mColumns - 1; j >= 0; j--) {
                        ImagePiece imagePiece = curImagePieces.get(i * mColumns + j);
                        if (imagePiece.direction == -1) {
                            if ((mRows - i) % 2 == 1) {
                                //从右往左滑动
                                imagePiece.direction = 0;
                            } else {
                                //从左往右滑动
                                imagePiece.direction = 1;
                            }
                        }
                        drawSnake(canvas, imagePiece, distance * transformRatio, oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j], 0, 1);
                    }
                }
            }
        } else {
            float transformRatio = Math.abs(deltaDistance) / (mWidth / 16);

            if (transformRatio <= 1) {
/*                for(int i = 0;i < mRows;i++) {
                    for (int j = 0; j < mColumns; j++) {
                        ImagePiece imagePiece = curImagePieces.get(i * mColumns + j);
                        if (imagePiece.direction == -1) {
                            if (i % 2 == 1) {
                                imagePiece.direction = 0;
                            } else {
                                imagePiece.direction = 1;
                            }
                        }
                    }
                }*/
                for (int i = 0; i < mRows; i++) {
                    for (int j = 0; j < mColumns; j++) {
                        ImagePiece imagePiece = curImagePieces.get(i * mColumns + j);

                        mMatrix.reset();
                        canvas.save();

                        mMatrix.preScale(1 - (1 - scaleX) * transformRatio, 1 - (1 - scaleY) * transformRatio);
                        mMatrix.preTranslate(-(imagePiece.bitmap.getWidth() / 2), -(imagePiece.bitmap.getHeight() / 2));
                        mMatrix.postTranslate(+(imagePiece.bitmap.getWidth() / 2), +(imagePiece.bitmap.getHeight() / 2));
                        mMatrix.postTranslate(oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j]);
                        canvas.drawBitmap(imagePiece.bitmap, mMatrix, null);

                        canvas.restore();
                    }
                }
            } else {
                transformRatio = (Math.abs(deltaDistance) - mWidth / 16) / (mWidth / 2);
                transformRatio = transformRatio > 1.0f ? 1.0f : transformRatio;

                for (int i = 0; i < mRows; i++) {
                    for (int j = 0; j < mColumns; j++) {
                        ImagePiece imagePiece = curImagePieces.get(i * mColumns + j);
                        if (imagePiece.direction == -1) {
                            if (i % 2 == 1) {
                                imagePiece.direction = 0;
                            } else {
                                imagePiece.direction = 1;
                            }
                        }
                        drawSnake(canvas, imagePiece, distance * transformRatio, oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j], 0, -1);
                    }
                }
            }
        }
    }

    private void drawNextPageArea(Canvas canvas, Bitmap bitmap) {
        /**
         * 将图片切成mColumn*mColumn份
         */
        if (isSplitNextImage) {
            isSplitNextImage = false;
            nextImagePieces = split(bitmap, mRows, mColumns);

            if (!isSetCoord) {
                initData();
                isSetCoord = true;
            }
        }

        if (deltaDistance <= 0) {
            float transformRatio = Math.abs(deltaDistance) / (mWidth / 32);

            if (transformRatio <= 1) {
/*                for(int i = 0;i < mRows;i++) {
                    for (int j = 0; j < mColumns; j++) {
                        ImagePiece imagePiece = nextImagePieces.get(i * mColumns + j);
                        if (imagePiece.direction == -1) {
                            if ((mRows - i) % 2 == 1) {
                                imagePiece.direction = 0;
                            } else {
                                imagePiece.direction = 1;
                            }
                        }
                    }
                }*/
                for (int i = 0; i < mRows; i++) {
                    for (int j = 0; j < mColumns; j++) {
                        ImagePiece imagePiece = nextImagePieces.get(i * mColumns + j);

                        mMatrix.reset();
                        canvas.save();

                        mMatrix.preScale(1 - (1 - scaleX) * transformRatio, 1 - (1 - scaleY) * transformRatio);
                        mMatrix.preTranslate(-(imagePiece.bitmap.getWidth() / 2), -(imagePiece.bitmap.getHeight() / 2));
                        mMatrix.postTranslate(+(imagePiece.bitmap.getWidth() / 2), +(imagePiece.bitmap.getHeight() / 2));
                        mMatrix.postTranslate(oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j] - getMeasuredHeight() - getMeasuredHeight() / mRows);
                        canvas.drawBitmap(imagePiece.bitmap, mMatrix, null);

                        canvas.restore();
                    }
                }
            } else {
                transformRatio = (Math.abs(deltaDistance) - mWidth / 32) / (mWidth / 32);
                if (transformRatio <= 1) {
                    for (int i = mRows - 1; i >= 0; i--) {
                        for (int j = mColumns - 1; j >= 0; j--) {
                            ImagePiece imagePiece = nextImagePieces.get(i * mColumns + j);
                            if (imagePiece.direction == -1) {
                                if ((mRows - i) % 2 == 1) {
                                    imagePiece.direction = 0;
                                } else {
                                    imagePiece.direction = 1;
                                }
                            }
                            drawSnake(canvas, imagePiece, offsetDownDistance * transformRatio, oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j], offsetDownHeight, 1);
                        }
                    }
                } else {
                    transformRatio = (Math.abs(deltaDistance) - mWidth / 16) / (mWidth / 2);
                    if (transformRatio <= 1.0f) {
                        for (int i = mRows - 1; i >= 0; i--) {
                            for (int j = mColumns - 1; j >= 0; j--) {
                                ImagePiece imagePiece = nextImagePieces.get(i * mColumns + j);
                                if (imagePiece.direction == -1) {
                                    if ((mRows - i) % 2 == 1) {
                                        imagePiece.direction = 0;
                                    } else {
                                        imagePiece.direction = 1;
                                    }
                                }
                                drawSnake(canvas, imagePiece, distance * transformRatio + offsetDownDistance, oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j], offsetDownHeight, 1);
                            }
                        }
                    } else {
                        transformRatio = (Math.abs(deltaDistance) - mWidth / 2) / (mWidth / 4);
                        transformRatio = transformRatio > 1.0f ? 1.0f : transformRatio;

                        for (int i = 0; i < mRows; i++) {
                            for (int j = 0; j < mColumns; j++) {
                                ImagePiece imagePiece = nextImagePieces.get(i * mColumns + j);

                                mMatrix.reset();
                                canvas.save();

                                mMatrix.preScale(scaleX + (1 - scaleX) * transformRatio, scaleY + (1 - scaleY) * transformRatio);
                                mMatrix.preTranslate(-(imagePiece.bitmap.getWidth() / 2), -(imagePiece.bitmap.getHeight() / 2));
                                mMatrix.postTranslate(+(imagePiece.bitmap.getWidth() / 2), +(imagePiece.bitmap.getHeight() / 2));
                                mMatrix.postTranslate(oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j]);
                                canvas.drawBitmap(imagePiece.bitmap, mMatrix, null);

                                canvas.restore();
                            }
                        }
                    }
                }
            }
        } else {
            float transformRatio = Math.abs(deltaDistance) / (mWidth / 32);

            if (transformRatio <= 1) {
/*                for(int i = 0;i < mRows;i++) {
                    for (int j = 0; j < mColumns; j++) {
                        ImagePiece imagePiece = nextImagePieces.get(i * mColumns + j);
                        if (imagePiece.direction == -1) {
                            if (i % 2 == 1) {
                                imagePiece.direction = 0;
                            } else {
                                imagePiece.direction = 1;
                            }
                        }
                    }
                }*/
                for (int i = 0; i < mRows; i++) {
                    for (int j = 0; j < mColumns; j++) {
                        ImagePiece imagePiece = nextImagePieces.get(i * mColumns + j);

                        mMatrix.reset();
                        canvas.save();

                        mMatrix.preScale(1 - (1 - scaleX) * transformRatio, 1 - (1 - scaleY) * transformRatio);
                        mMatrix.preTranslate(-(imagePiece.bitmap.getWidth() / 2), -(imagePiece.bitmap.getHeight() / 2));
                        mMatrix.postTranslate(+(imagePiece.bitmap.getWidth() / 2), +(imagePiece.bitmap.getHeight() / 2));
                        mMatrix.postTranslate(oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j] + getMeasuredHeight() + getMeasuredHeight() / mRows);
                        canvas.drawBitmap(imagePiece.bitmap, mMatrix, null);

                        canvas.restore();
                    }
                }
            } else {
                transformRatio = (Math.abs(deltaDistance) - mWidth / 32) / (mWidth / 32);
                if (transformRatio <= 1) {
                    for (int i = 0; i < mRows; i++) {
                        for (int j = 0; j < mColumns; j++) {
                            ImagePiece imagePiece = nextImagePieces.get(i * mColumns + j);
                            if (imagePiece.direction == -1) {
                                if (i % 2 == 1) {
                                    imagePiece.direction = 0;
                                } else {
                                    imagePiece.direction = 1;
                                }
                            }
                            drawSnake(canvas, imagePiece, offsetUpDistance * transformRatio, oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j], offsetUpHeight, -1);
                        }
                    }
                } else {
                    transformRatio = (Math.abs(deltaDistance) - mWidth / 16) / (mWidth / 2);

                    if (transformRatio <= 1.0f) {
                        for (int i = 0; i < mRows; i++) {
                            for (int j = 0; j < mColumns; j++) {
                                ImagePiece imagePiece = nextImagePieces.get(i * mColumns + j);
                                if (imagePiece.direction == -1) {
                                    if (i % 2 == 1) {
                                        imagePiece.direction = 0;
                                    } else {
                                        imagePiece.direction = 1;
                                    }
                                }
                                drawSnake(canvas, imagePiece, distance * transformRatio + offsetUpDistance, oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j], offsetUpHeight, -1);
                            }
                        }
                    } else {
                        transformRatio = (Math.abs(deltaDistance) - mWidth / 2) / (mWidth / 4);
                        transformRatio = transformRatio > 1.0f ? 1.0f : transformRatio;

                        for (int i = 0; i < mRows; i++) {
                            for (int j = 0; j < mColumns; j++) {
                                ImagePiece imagePiece = nextImagePieces.get(i * mColumns + j);

                                mMatrix.reset();
                                canvas.save();

                                mMatrix.preScale(scaleX + (1 - scaleX) * transformRatio, scaleY + (1 - scaleY) * transformRatio);
                                mMatrix.preTranslate(-(imagePiece.bitmap.getWidth() / 2), -(imagePiece.bitmap.getHeight() / 2));
                                mMatrix.postTranslate(+(imagePiece.bitmap.getWidth() / 2), +(imagePiece.bitmap.getHeight() / 2));
                                mMatrix.postTranslate(oldCoordX[i * mColumns + j], oldCoordY[i * mColumns + j]);
                                canvas.drawBitmap(imagePiece.bitmap, mMatrix, null);

                                canvas.restore();
                            }
                        }
                    }
                }
            }
        }

    }

    public void setBitmaps(Bitmap bm1, Bitmap bm2) {
        mCurPageBitmap = bm1;
        mNextPageBitmap = bm2;
        isSplitCurImage = true;
        isSplitNextImage = true;
        this.postInvalidate();
    }

    public boolean doTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            mTouch.x = (float) Math.ceil(event.getX());
            mTouch.y = (float) Math.ceil(event.getY());
            this.postInvalidate();
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mFirstTouch.x = (float) Math.ceil(event.getX());
            mFirstTouch.y = (float) Math.ceil(event.getY());
            mTouch.x = (float) Math.ceil(event.getX());
            mTouch.y = (float) Math.ceil(event.getY());
            deltaDistance = 0.0f;
            this.postInvalidate();

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, "scaleX", 1.0f, 0.8f),
                    ObjectAnimator.ofFloat(this, "scaleY", 1.0f, 0.8f),
                    ObjectAnimator.ofFloat(this, "translationY", 0, (float) -(mHeight - Math.cos(rotateX * Math.PI / 180) * mHeight)),
                    ObjectAnimator.ofFloat(this, "rotationX", 0, rotateX)
            );
            animatorSet.setDuration(500).start();

        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (canDragOver()) {
                startAnimation(2000);
            } else {
                startAnimation(1500);
            }
            this.postInvalidate();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(Pager.this, "scaleX", 0.8f, 1.0f),
                            ObjectAnimator.ofFloat(Pager.this, "scaleY", 0.8f, 1.0f),
                            ObjectAnimator.ofFloat(Pager.this, "translationY", (float) -(mHeight - Math.cos(rotateX * Math.PI / 180) * mHeight), 0),
                            ObjectAnimator.ofFloat(Pager.this, "rotationX", rotateX, 0)
                    );
                    animatorSet.setDuration(500).start();
                }
            }, 500);
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //canvas.drawColor(0xFFAAAAAA);
        deltaDistance = mTouch.x - mFirstTouch.x;
        if (deltaDistance < 0.0f) {
            if (deltaDistance > -2.0f) {
                deltaDistance = 0.0f;
            }
        } else {
            if (deltaDistance < 2.0f) {
                deltaDistance = 0.0f;
            }
        }
        if (!isCurrentFirst) {
            if (mNextPageBitmap != null) {
                //后半球
                drawNextPageArea(canvas, mNextPageBitmap);
            }
            //前半球
            drawCurPageArea(canvas, mCurPageBitmap);
        } else {
            //前半球
            drawCurPageArea(canvas, mCurPageBitmap);
            if (mNextPageBitmap != null) {
                //后半球
                drawNextPageArea(canvas, mNextPageBitmap);
            }
        }

        drawGlass(canvas);
    }

    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            float x = mScroller.getCurrX();
            float y = mScroller.getCurrY();
            mTouch.x = x;
            mTouch.y = y;
            postInvalidate();
        }
    }

    private void startAnimation(int delayMillis) {
        int dx;
        float tmpDeltaDistance = mTouch.x - mFirstTouch.x;
        if (tmpDeltaDistance < 0) {
            if (Math.abs(tmpDeltaDistance) > mWidth / 2) {
                dx = -(mWidth + 10);
            } else {
                dx = (int) Math.abs(tmpDeltaDistance);
            }
        } else {
            if (tmpDeltaDistance > mWidth / 2) {
                dx = (mWidth + 10);
            } else {
                dx = (int) -tmpDeltaDistance;
            }
        }
        mScroller.startScroll((int) mTouch.x, 0, dx, 0, delayMillis);
    }

    public void abortAnimation() {
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
    }

    public boolean isAnimationRunning() {
        return !mScroller.isFinished();
    }

    public boolean canDragOver() {
        if (Math.abs(deltaDistance) > mWidth / 2)
            return true;
        return false;
    }
}
