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
import jp.co.yahoo.yosegi.binary.CompressResultNode;
import jp.co.yahoo.yosegi.binary.maker.index.RangeFloatIndex;
import jp.co.yahoo.yosegi.blockindex.BlockIndexNode;
import jp.co.yahoo.yosegi.blockindex.FloatRangeBlockIndex;
import jp.co.yahoo.yosegi.compressor.FindCompressor;
import jp.co.yahoo.yosegi.compressor.ICompressor;
import jp.co.yahoo.yosegi.inmemory.IMemoryAllocator;
import jp.co.yahoo.yosegi.message.objects.FloatObj;
import jp.co.yahoo.yosegi.message.objects.PrimitiveObject;
import jp.co.yahoo.yosegi.spread.analyzer.IColumnAnalizeResult;
import jp.co.yahoo.yosegi.spread.column.ColumnType;
import jp.co.yahoo.yosegi.spread.column.ICell;
import jp.co.yahoo.yosegi.spread.column.IColumn;
import jp.co.yahoo.yosegi.spread.column.PrimitiveCell;
import jp.co.yahoo.yosegi.spread.column.PrimitiveColumn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class RangeDumpFloatColumnBinaryMaker extends DumpFloatColumnBinaryMaker {

  private static final int HEADER_SIZE = ( Float.BYTES * 2 ) + Integer.BYTES;

  @Override
  public ColumnBinary toBinary(
      final ColumnBinaryMakerConfig commonConfig ,
      final ColumnBinaryMakerCustomConfigNode currentConfigNode ,
      final CompressResultNode compressResultNode ,
      final IColumn column ) throws IOException {
    ColumnBinaryMakerConfig currentConfig = commonConfig;
    if ( currentConfigNode != null ) {
      currentConfig = currentConfigNode.getCurrentConfig();
    }
    byte[] parentsBinaryRaw =
        new byte[ ( Integer.BYTES * 2 ) + column.size() + ( column.size() * Float.BYTES ) ];
    ByteBuffer lengthBuffer = ByteBuffer.wrap( parentsBinaryRaw );
    lengthBuffer.putInt( column.size() );
    lengthBuffer.putInt( column.size() * Float.BYTES );

    ByteBuffer nullFlagBuffer =
        ByteBuffer.wrap( parentsBinaryRaw , Integer.BYTES * 2 , column.size() );
    FloatBuffer floatBuffer = ByteBuffer.wrap(
        parentsBinaryRaw ,
        ( Integer.BYTES * 2 + column.size() ) ,
        ( column.size() * Float.BYTES ) ).asFloatBuffer();
    int rowCount = 0;
    boolean hasNull = false;
    Float min = Float.MAX_VALUE;
    Float max = Float.MIN_VALUE;
    for ( int i = 0 ; i < column.size() ; i++ ) {
      ICell cell = column.get(i);
      if ( cell.getType() == ColumnType.NULL ) {
        nullFlagBuffer.put( (byte)1 );
        floatBuffer.put( (float)0 );
        hasNull = true;
      } else {
        rowCount++;
        PrimitiveCell byteCell = (PrimitiveCell) cell;
        nullFlagBuffer.put( (byte)0 );
        Float target = Float.valueOf( byteCell.getRow().getFloat() );
        floatBuffer.put( target );
        if ( 0 < min.compareTo( target ) ) {
          min = Float.valueOf( target );
        }
        if ( max.compareTo( target ) < 0 ) {
          max = Float.valueOf( target );
        }
      }
    }

    if ( ! hasNull && min.equals( max ) ) {
      return ConstantColumnBinaryMaker.createColumnBinary(
          new FloatObj( min ) , column.getColumnName() , column.size() );
    }

    byte[] binary;
    int rawLength;
    if ( hasNull ) {
      byte[] binaryRaw = parentsBinaryRaw;
      byte[] compressBinaryRaw =
          currentConfig.compressorClass.compress( binaryRaw , 0 , binaryRaw.length );
      rawLength = binaryRaw.length;
      
      binary = new byte[ HEADER_SIZE + compressBinaryRaw.length ];
      ByteBuffer wrapBuffer = ByteBuffer.wrap( binary );
      wrapBuffer.putFloat( min );
      wrapBuffer.putFloat( max );
      wrapBuffer.putInt( 0 );
      wrapBuffer.put( compressBinaryRaw );
    } else {
      rawLength = column.size() * Float.BYTES;
      byte[] compressBinaryRaw = currentConfig.compressorClass.compress(
          parentsBinaryRaw , ( Integer.BYTES * 2 ) + column.size() , column.size() * Float.BYTES );

      binary = new byte[ HEADER_SIZE + compressBinaryRaw.length ];
      ByteBuffer wrapBuffer = ByteBuffer.wrap( binary );
      wrapBuffer.putFloat( min );
      wrapBuffer.putFloat( max );
      wrapBuffer.putInt( 1 );
      wrapBuffer.put( compressBinaryRaw );
    }
    return new ColumnBinary(
        this.getClass().getName() ,
        currentConfig.compressorClass.getClass().getName() ,
        column.getColumnName() ,
        ColumnType.FLOAT ,
        rowCount ,
        rawLength ,
        rowCount * Float.BYTES ,
        -1 ,
        binary ,
        0 ,
        binary.length ,
        null );
  }

  @Override
  public int calcBinarySize( final IColumnAnalizeResult analizeResult ) {
    if ( analizeResult.getNullCount() == 0 && analizeResult.getUniqCount() == 1 ) {
      return Float.BYTES;
    } else if ( analizeResult.getNullCount() == 0 ) {
      return analizeResult.getColumnSize() * Float.BYTES;
    } else {
      return super.calcBinarySize( analizeResult );
    }
  }

  @Override
  public IColumn toColumn( final ColumnBinary columnBinary ) throws IOException {
    ByteBuffer wrapBuffer = ByteBuffer.wrap(
        columnBinary.binary , columnBinary.binaryStart , columnBinary.binaryLength );
    Float min = Float.valueOf( wrapBuffer.getFloat() );
    Float max = Float.valueOf( wrapBuffer.getFloat() );
    int type = wrapBuffer.getInt();
    if ( type == 0 ) {
      return new HeaderIndexLazyColumn( 
        columnBinary.columnName , 
        columnBinary.columnType , 
        new FloatColumnManager(
            columnBinary , 
            columnBinary.binaryStart + HEADER_SIZE , 
            columnBinary.binaryLength - HEADER_SIZE ) , 
            new RangeFloatIndex( min , max ) );
    } else {
      return new HeaderIndexLazyColumn(
        columnBinary.columnName ,
        columnBinary.columnType ,
        new RangeFloatColumnManager(
            columnBinary ,
            columnBinary.binaryStart + HEADER_SIZE ,
            columnBinary.binaryLength - HEADER_SIZE ) ,
            new RangeFloatIndex( min , max ) );
    }
  }

  @Override
  public void loadInMemoryStorage(
      final ColumnBinary columnBinary , final IMemoryAllocator allocator ) throws IOException {
    ByteBuffer wrapBuffer = ByteBuffer.wrap(
        columnBinary.binary , columnBinary.binaryStart , columnBinary.binaryLength );
    wrapBuffer.position( Float.BYTES * 2 );
    int type = wrapBuffer.getInt();
    if ( type == 0 ) {
      loadInMemoryStorage(
          columnBinary ,
          columnBinary.binaryStart + HEADER_SIZE ,
          columnBinary.binaryLength - HEADER_SIZE ,
          allocator );
      return;
    }
    ICompressor compressor = FindCompressor.get( columnBinary.compressorClassName );
    byte[] binary = compressor.decompress(
        columnBinary.binary ,
        columnBinary.binaryStart + HEADER_SIZE ,
        columnBinary.binaryLength - HEADER_SIZE );
    wrapBuffer = wrapBuffer.wrap( binary );
    for ( int i = 0 ; i < columnBinary.rowCount ; i++ ) {
      allocator.setFloat( i , wrapBuffer.getFloat() );
    }
    allocator.setValueCount( columnBinary.rowCount );
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

  public class RangeFloatDicManager implements IDicManager {

    private final FloatBuffer dicBuffer;
    private final int dicLength;

    public RangeFloatDicManager( final FloatBuffer dicBuffer ) {
      this.dicBuffer = dicBuffer;
      dicLength = dicBuffer.capacity(); 
    }

    @Override
    public PrimitiveObject get( final int index ) throws IOException {
      return new FloatObj( dicBuffer.get( index ) );
    }

    @Override
    public int getDicSize() throws IOException {
      return dicLength;
    }

  }

  public class RangeFloatColumnManager implements IColumnManager {

    private final ColumnBinary columnBinary;
    private final int binaryStart;
    private final int binaryLength;
    private PrimitiveColumn column;
    private boolean isCreate;

    /**
     * Initialize by setting binary.
     */
    public RangeFloatColumnManager(
        final ColumnBinary columnBinary ,
        final int binaryStart ,
        final int binaryLength ) throws IOException {
      this.columnBinary = columnBinary;
      this.binaryStart = binaryStart;
      this.binaryLength = binaryLength;
    }

    private void create() throws IOException {
      if ( isCreate ) {
        return;
      }
      ICompressor compressor = FindCompressor.get( columnBinary.compressorClassName );
      byte[] binary = compressor.decompress( columnBinary.binary , binaryStart , binaryLength );

      column = new PrimitiveColumn( columnBinary.columnType , columnBinary.columnName );
      IDicManager dicManager =
          new RangeFloatDicManager( ByteBuffer.wrap( binary ).asFloatBuffer() );
      column.setCellManager( new BufferDirectCellManager(
          columnBinary.columnType , dicManager , columnBinary.rowCount ) );

      isCreate = true;
    }

    @Override
    public IColumn get() {
      try {
        create();
      } catch ( IOException ex ) {
        throw new UncheckedIOException( ex );
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
