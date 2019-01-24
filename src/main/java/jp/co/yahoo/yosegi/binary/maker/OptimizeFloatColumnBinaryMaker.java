/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.yahoo.yosegi.binary.maker;

import jp.co.yahoo.yosegi.binary.ColumnBinary;
import jp.co.yahoo.yosegi.binary.ColumnBinaryMakerConfig;
import jp.co.yahoo.yosegi.binary.ColumnBinaryMakerCustomConfigNode;
import jp.co.yahoo.yosegi.binary.maker.index.BufferDirectSequentialNumberCellIndex;
import jp.co.yahoo.yosegi.binary.maker.index.RangeFloatIndex;
import jp.co.yahoo.yosegi.blockindex.BlockIndexNode;
import jp.co.yahoo.yosegi.blockindex.FloatRangeBlockIndex;
import jp.co.yahoo.yosegi.compressor.FindCompressor;
import jp.co.yahoo.yosegi.compressor.ICompressor;
import jp.co.yahoo.yosegi.inmemory.IMemoryAllocator;
import jp.co.yahoo.yosegi.message.objects.FloatObj;
import jp.co.yahoo.yosegi.message.objects.PrimitiveObject;
import jp.co.yahoo.yosegi.spread.analyzer.FloatColumnAnalizeResult;
import jp.co.yahoo.yosegi.spread.analyzer.IColumnAnalizeResult;
import jp.co.yahoo.yosegi.spread.column.ColumnType;
import jp.co.yahoo.yosegi.spread.column.ICell;
import jp.co.yahoo.yosegi.spread.column.IColumn;
import jp.co.yahoo.yosegi.spread.column.PrimitiveCell;
import jp.co.yahoo.yosegi.spread.column.PrimitiveColumn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptimizeFloatColumnBinaryMaker implements IColumnBinaryMaker {

  public static IDictionaryMaker chooseDictionaryMaker( final float min , final float max ) {
    return new FloatDictionaryMaker();
  }

  /**
   * Create IDictionaryIndexMaker of the smallest type
   * from the maximum value of the dictionary index.
   */
  public static IDictionaryIndexMaker chooseDictionaryIndexMaker( final int dicIndexLength ) {
    if ( dicIndexLength < Byte.valueOf( Byte.MAX_VALUE ).intValue() ) {
      return new ByteDictionaryIndexMaker();
    } else if ( dicIndexLength < Short.valueOf( Short.MAX_VALUE ).intValue() ) {
      return new ShortDictionaryIndexMaker();
    } else {
      return new IntDictionaryIndexMaker();
    }
  }

  public interface IDictionaryMaker {

    int getLogicalSize( final int indexLength );

    int calcBinarySize( final int dicSize );

    void create(
        final List<PrimitiveObject> objList ,
        final byte[] buffer ,
        final int start ,
        final int length ) throws IOException;

    PrimitiveObject[] getDicPrimitiveArray(
        final byte[] buffer , final int start , final int length ) throws IOException;

  }

  public static class FloatDictionaryMaker implements IDictionaryMaker {

    @Override
    public int getLogicalSize( final int indexLength ) {
      return Float.BYTES * indexLength;
    }

    @Override
    public int calcBinarySize( final int dicSize ) {
      return Float.BYTES * dicSize;
    }

    @Override
    public void create(
        final List<PrimitiveObject> objList ,
        final byte[] buffer ,
        final int start ,
        final int length ) throws IOException {
      ByteBuffer wrapBuffer = ByteBuffer.wrap( buffer , start , length );
      for ( PrimitiveObject obj : objList ) {
        wrapBuffer.putFloat( obj.getFloat() );
      }
    }

    @Override
      public PrimitiveObject[] getDicPrimitiveArray(
        final byte[] buffer , final int start , final int length ) throws IOException {
      int size = length / Float.BYTES;
      PrimitiveObject[] result = new PrimitiveObject[size];
      ByteBuffer wrapBuffer = ByteBuffer.wrap( buffer , start , length );
      wrapBuffer.getFloat();
      for ( int i = 1 ; i < size ; i++ ) {
        result[i] = new FloatObj( wrapBuffer.getFloat() );
      }

      return result;
    }

  }

  public interface IDictionaryIndexMaker {

    int calcBinarySize( final int indexLength );

    void create(
        final int[] dicIndexArray ,
        final byte[] buffer ,
        final int start ,
        final int length ) throws IOException;

    IntBuffer getIndexIntBuffer(
        final byte[] buffer , final int start , final int length ) throws IOException;

  }

  public static class ByteDictionaryIndexMaker implements IDictionaryIndexMaker {

    @Override
    public int calcBinarySize( final int indexLength ) {
      return Byte.BYTES * indexLength;
    }

    @Override
    public void create(
        final int[] dicIndexArray ,
        final byte[] buffer ,
        final int start ,
        final int length ) throws IOException {
      ByteBuffer wrapBuffer = ByteBuffer.wrap( buffer , start , length );
      for ( int index : dicIndexArray ) {
        wrapBuffer.put( (byte)index );
      }
    }

    @Override
    public IntBuffer getIndexIntBuffer(
        final byte[] buffer , final int start , final int length ) throws IOException {
      int size = length / Byte.BYTES;
      ByteBuffer wrapBuffer = ByteBuffer.wrap( buffer , start , length );
      IntBuffer result = IntBuffer.allocate( size );
      for ( int i = 0 ; i < size ; i++ ) {
        result.put( (int)( wrapBuffer.get() ) );
      }
      result.position( 0 );
      return result;
    }

  }

  public static class ShortDictionaryIndexMaker implements IDictionaryIndexMaker {

    @Override
    public int calcBinarySize( final int indexLength ) {
      return Short.BYTES * indexLength;
    }

    @Override
    public void create(
        final int[] dicIndexArray ,
        final byte[] buffer ,
        final int start ,
        final int length ) throws IOException {
      ByteBuffer wrapBuffer = ByteBuffer.wrap( buffer , start , length );
      for ( int index : dicIndexArray ) {
        wrapBuffer.putShort( (short)index );
      }
    }

    @Override
    public IntBuffer getIndexIntBuffer(
        final byte[] buffer , final int start , final int length ) throws IOException {
      int size = length / Short.BYTES;
      ByteBuffer wrapBuffer = ByteBuffer.wrap( buffer , start , length );
      IntBuffer result = IntBuffer.allocate( size );
      for ( int i = 0 ; i < size ; i++ ) {
        result.put( (int)( wrapBuffer.getShort() ) );
      }
      result.position( 0 );
      return result;
    }

  }

  public static class IntDictionaryIndexMaker implements IDictionaryIndexMaker {

    @Override
    public int calcBinarySize( final int indexLength ) {
      return Integer.BYTES * indexLength;
    }

    @Override
    public void create(
        final int[] dicIndexArray ,
        final byte[] buffer ,
        final int start ,
        final int length ) throws IOException {
      ByteBuffer wrapBuffer = ByteBuffer.wrap( buffer , start , length );
      for ( int index : dicIndexArray ) {
        wrapBuffer.putInt( index );
      }
    }

    @Override
    public IntBuffer getIndexIntBuffer(
        final byte[] buffer , final int start , final int length ) throws IOException {
      return ByteBuffer.wrap( buffer , start , length ).asIntBuffer();
    }

  }

  @Override
  public ColumnBinary toBinary(
      final ColumnBinaryMakerConfig commonConfig ,
      final ColumnBinaryMakerCustomConfigNode currentConfigNode ,
      final IColumn column ) throws IOException {
    ColumnBinaryMakerConfig currentConfig = commonConfig;
    if ( currentConfigNode != null ) {
      currentConfig = currentConfigNode.getCurrentConfig();
    }
    Map<Float,Integer> dicMap = new HashMap<Float,Integer>();
    List<PrimitiveObject> dicList = new ArrayList<PrimitiveObject>();
    int[] indexArray = new int[column.size()];

    dicMap.put( null , Integer.valueOf(0) );
    dicList.add( new FloatObj( (float)0 ) );

    Float min = Float.MAX_VALUE;
    Float max = Float.MIN_VALUE;
    int rowCount = 0;
    boolean hasNull = false;
    for ( int i = 0 ; i < column.size() ; i++ ) {
      ICell cell = column.get(i);
      PrimitiveObject primitiveObj = null;
      Float target = null;
      if ( cell.getType() == ColumnType.NULL ) {
        hasNull = true;
      } else {
        rowCount++;
        PrimitiveCell stringCell = (PrimitiveCell) cell;
        primitiveObj = stringCell.getRow();
        target = Float.valueOf( primitiveObj.getFloat() );
      }
      if ( ! dicMap.containsKey( target ) ) {
        if ( 0 < min.compareTo( target ) ) {
          min = Float.valueOf( target );
        }
        if ( max.compareTo( target ) < 0 ) {
          max = Float.valueOf( target );
        }
        dicMap.put( target , dicMap.size() );
        dicList.add( primitiveObj );
      }
      indexArray[i] = dicMap.get( target );
    }

    if ( ! hasNull && min.equals( max ) ) {
      return ConstantColumnBinaryMaker.createColumnBinary(
          new FloatObj( min ) , column.getColumnName() , column.size() );
    }

    IDictionaryIndexMaker indexMaker = chooseDictionaryIndexMaker( indexArray.length );
    IDictionaryMaker dicMaker = chooseDictionaryMaker( min.floatValue() , max.floatValue() );

    int indexLength = indexMaker.calcBinarySize( indexArray.length );
    int dicLength = dicMaker.calcBinarySize( dicList.size() );

    byte[] binaryRaw = new byte[ indexLength + dicLength ];
    indexMaker.create( indexArray , binaryRaw , 0 , indexLength );
    dicMaker.create( dicList , binaryRaw , indexLength , dicLength );

    byte[] compressBinary =
        currentConfig.compressorClass.compress( binaryRaw , 0 , binaryRaw.length );

    byte[] binary = new byte[ Float.BYTES * 2 + compressBinary.length ];

    ByteBuffer wrapBuffer = ByteBuffer.wrap( binary , 0 , binary.length );
    wrapBuffer.putFloat( min );
    wrapBuffer.putFloat( max );
    wrapBuffer.put( compressBinary );

    return new ColumnBinary(
        this.getClass().getName() ,
        currentConfig.compressorClass.getClass().getName() ,
        column.getColumnName() ,
        column.getColumnType() ,
        column.size() ,
        binary.length ,
        dicMaker.getLogicalSize( rowCount ) ,
        dicList.size() ,
        binary ,
        0 ,
        binary.length ,
        null );
  }

  @Override
  public int calcBinarySize( final IColumnAnalizeResult analizeResult ) {
    float min = ( (FloatColumnAnalizeResult) analizeResult ).getMin();
    float max = ( (FloatColumnAnalizeResult) analizeResult ).getMax();
    IDictionaryIndexMaker indexMaker = chooseDictionaryIndexMaker( analizeResult.getColumnSize() );
    IDictionaryMaker dicMaker = chooseDictionaryMaker( min , max );

    int indexLength = indexMaker.calcBinarySize( analizeResult.getColumnSize() );
    int dicLength = dicMaker.calcBinarySize( analizeResult.getUniqCount() );

    return indexLength + dicLength;
  }

  @Override
  public IColumn toColumn( final ColumnBinary columnBinary ) throws IOException {
    ByteBuffer wrapBuffer = ByteBuffer.wrap(
        columnBinary.binary , columnBinary.binaryStart , columnBinary.binaryLength );
    Float min = Float.valueOf( wrapBuffer.getFloat() );
    Float max = Float.valueOf( wrapBuffer.getFloat() );

    IDictionaryIndexMaker indexMaker = chooseDictionaryIndexMaker( columnBinary.rowCount );
    IDictionaryMaker dicMaker = chooseDictionaryMaker( min.floatValue() , max.floatValue() );
    return new HeaderIndexLazyColumn(
        columnBinary.columnName ,
        columnBinary.columnType ,
        new ColumnManager(
          columnBinary ,
          indexMaker ,
          dicMaker
        ) ,
        new RangeFloatIndex( min , max ) );
  }

  @Override
  public void loadInMemoryStorage(
      final ColumnBinary columnBinary ,
      final IMemoryAllocator allocator ) throws IOException {
    ByteBuffer wrapBuffer = ByteBuffer.wrap(
        columnBinary.binary , columnBinary.binaryStart , columnBinary.binaryLength );
    Float min = Float.valueOf( wrapBuffer.getFloat() );
    Float max = Float.valueOf( wrapBuffer.getFloat() );

    IDictionaryIndexMaker indexMaker = chooseDictionaryIndexMaker( columnBinary.rowCount );
    IDictionaryMaker dicMaker = chooseDictionaryMaker( min.floatValue() , max.floatValue() );

    int start = columnBinary.binaryStart + ( Float.BYTES * 2 );
    int length = columnBinary.binaryLength - ( Float.BYTES * 2 );

    ICompressor compressor = FindCompressor.get( columnBinary.compressorClassName );
    byte[] binary = compressor.decompress( columnBinary.binary , start , length );

    int indexLength = indexMaker.calcBinarySize( columnBinary.rowCount );
    int dicLength = dicMaker.calcBinarySize( columnBinary.cardinality );

    IntBuffer indexIntBuffer = indexMaker.getIndexIntBuffer( binary , 0 , indexLength );
    PrimitiveObject[] dicArray = dicMaker.getDicPrimitiveArray( binary , indexLength , dicLength );

    int loopCount = indexIntBuffer.capacity();
    for ( int i = 0 ; i < loopCount ; i++ ) {
      int dicIndex = indexIntBuffer.get();
      if ( dicIndex != 0 ) {
        allocator.setFloat( i , dicArray[dicIndex].getFloat() );
      }
    }
    allocator.setValueCount( loopCount );
  }

  @Override
  public void setBlockIndexNode(
      final BlockIndexNode parentNode ,
      final ColumnBinary columnBinary ,
      final int spreadIndex ) throws IOException {
    ByteBuffer wrapBuffer = ByteBuffer.wrap(
        columnBinary.binary , columnBinary.binaryStart , columnBinary.binaryLength );
    Float min = Float.valueOf( wrapBuffer.getFloat() );
    Float max = Float.valueOf( wrapBuffer.getFloat() );
    BlockIndexNode currentNode = parentNode.getChildNode( columnBinary.columnName );
    currentNode.setBlockIndex( new FloatRangeBlockIndex( min , max ) );
  }

  public class DicManager implements IDicManager {

    private final PrimitiveObject[] dicArray;

    public DicManager( final PrimitiveObject[] dicArray ) throws IOException {
      this.dicArray = dicArray;
    }

    @Override
    public PrimitiveObject get( final int index ) throws IOException {
      return dicArray[index];
    }

    @Override
    public int getDicSize() throws IOException {
      return dicArray.length;
    }

  }

  public class ColumnManager implements IColumnManager {

    private final ColumnBinary columnBinary;
    private final IDictionaryIndexMaker indexMaker;
    private final IDictionaryMaker dicMaker;

    private PrimitiveColumn column;
    private boolean isCreate;

    /**
     * Initialize by setting binary.
     */
    public ColumnManager(
        final ColumnBinary columnBinary ,
        final IDictionaryIndexMaker indexMaker ,
        final IDictionaryMaker dicMaker ) {
      this.columnBinary = columnBinary;
      this.indexMaker = indexMaker;
      this.dicMaker = dicMaker;
    }

    private void create() throws IOException {
      if ( isCreate ) {
        return;
      }
      int start = columnBinary.binaryStart + ( Float.BYTES * 2 );
      int length = columnBinary.binaryLength - ( Float.BYTES * 2 );

      ICompressor compressor = FindCompressor.get( columnBinary.compressorClassName );
      byte[] binary = compressor.decompress( columnBinary.binary , start , length );

      int indexLength = indexMaker.calcBinarySize( columnBinary.rowCount );
      int dicLength = dicMaker.calcBinarySize( columnBinary.cardinality );

      IntBuffer indexIntBuffer = indexMaker.getIndexIntBuffer( binary , 0 , indexLength );
      PrimitiveObject[] dicArray =
          dicMaker.getDicPrimitiveArray( binary , indexLength , dicLength );

      IDicManager dicManager = new DicManager( dicArray );
      column = new PrimitiveColumn( columnBinary.columnType , columnBinary.columnName );
      column.setCellManager( new BufferDirectDictionaryLinkCellManager(
          columnBinary.columnType , dicManager , indexIntBuffer ) );
      column.setIndex( new BufferDirectSequentialNumberCellIndex(
          columnBinary.columnType , dicManager , indexIntBuffer ) );

      isCreate = true;
    }

    @Override
    public IColumn get() {
      if ( ! isCreate ) {
        try {
          create();
        } catch ( IOException ex ) {
          throw new UncheckedIOException( ex );
        }
      }
      return column;
    }

    @Override
    public List<String> getColumnKeys() {
      return new ArrayList<String>();
    }

    @Override
    public int getColumnSize() {
      return 0;
    }

  }

}
