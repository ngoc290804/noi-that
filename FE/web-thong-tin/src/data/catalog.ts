import type { Category, Product, ProductImage } from '../types/catalog';

function image(name: string, alt: string, width = 1200, height = 896): ProductImage {
  return {
    src: `/images/${name}.webp`,
    thumbnail: `/images/${name}-600.webp`,
    alt,
    width,
    height,
  };
}

export const categories: readonly Category[] = [
  { id: 'ban-ghe', name: 'Ghế & Bàn', slug: 'ban-ghe' },
  { id: 'den', name: 'Đèn', slug: 'den' },
  { id: 'gom-su', name: 'Gốm sứ', slug: 'gom-su' },
];

const pendingSpecifications = [
  { label: 'Chất liệu', value: null },
  { label: 'Kích thước', value: null },
  { label: 'Màu sắc', value: null },
];

export const products: readonly Product[] = [
  {
    id: 'p-binh-gom-luma', slug: 'binh-gom-luma', name: 'Bình gốm Luma',
    categoryId: 'gom-su', priceVnd: 620000, displayOrder: 1, isFeatured: true,
    shortDescription: 'Một điểm nhấn nhẹ nhàng cho những góc nhà bạn yêu.',
    description: 'Dáng bình tròn mềm mại, sắc màu trung tính dễ kết hợp cùng nhiều không gian. Đặt riêng trên kệ, bên khung cửa sổ hoặc phối cùng một cành cây để tạo nên góc nhỏ của riêng bạn.',
    images: [image('binh-gom-luma', 'Bình gốm Luma sáng màu đặt trên tủ trong không gian có ánh sáng tự nhiên')],
    specifications: pendingSpecifications,
  },
  {
    id: 'p-ghe-an-nordic', slug: 'ghe-an-nordic', name: 'Ghế ăn Nordic',
    categoryId: 'ban-ghe', priceVnd: 2900000, displayOrder: 2, isFeatured: true,
    shortDescription: 'Những đường cong mộc mạc, dành cho góc sum vầy mỗi ngày.',
    description: 'Ghế ăn Nordic mang dáng vẻ thanh thoát với phần tựa lưng cong và mặt ngồi đan. Một gợi ý cho bàn ăn ấm cúng, góc đọc sách hoặc không gian bạn muốn dành thêm một chỗ ngồi.',
    images: [
      image('ghe-an-nordic', 'Ghế ăn Nordic có tựa lưng cong và mặt ngồi đan bên rèm cửa'),
      image('ghe-an-nordic-gan', 'Cận cảnh tựa lưng và mặt ngồi đan của Ghế ăn Nordic', 1024, 764),
    ],
    specifications: pendingSpecifications,
  },
  {
    id: 'p-den-ban-horizon', slug: 'den-ban-horizon', name: 'Đèn bàn Horizon',
    categoryId: 'den', priceVnd: 1250000, displayOrder: 3, isFeatured: true,
    shortDescription: 'Một quầng sáng ấm cho những khoảng lặng trong ngày.',
    description: 'Dáng vòm nhẹ nhàng và ánh sáng ấm tạo điểm nhấn cho chiếc tủ, góc làm việc hoặc bàn bên cạnh sofa. Đèn bàn Horizon gợi nên một không gian thư thái khi ngày dần khép lại.',
    images: [image('den-ban-horizon', 'Đèn bàn Horizon dáng vòm với ánh sáng ấm trên mặt tủ')],
    specifications: pendingSpecifications,
  },
  {
    id: 'p-bo-coc-gom-moc', slug: 'bo-coc-gom-moc', name: 'Bộ cốc Gốm Mộc',
    categoryId: 'gom-su', priceVnd: 450000, displayOrder: 4, isFeatured: true,
    shortDescription: 'Chậm lại một chút, bên thức uống bạn yêu thích.',
    description: 'Sắc gốm trung tính với những chấm màu tự nhiên tạo cảm giác gần gũi trên bàn trà. Bộ cốc Gốm Mộc là gợi ý nhỏ cho những buổi sáng thong thả và phút nghỉ giữa ngày.',
    images: [image('bo-coc-gom-moc', 'Hai cốc Gốm Mộc đặt trên khay gỗ cạnh tấm vải sáng màu')],
    specifications: pendingSpecifications,
  },
];

export const homeContent = {
  heroTitle: 'Những món đồ nhỏ làm nên tổ ấm lớn',
  heroDescription: 'Nội thất và gia dụng tinh giản, mang cảm hứng đến những không gian sống trọn vẹn hơn mỗi ngày.',
  heroImage: image('phong-khach-nordic', 'Phòng khách với sofa sáng màu, bàn tròn và ánh nắng qua rèm cửa'),
  featuredProductIds: products.filter((product) => product.isFeatured).map((product) => product.id),
  spaces: [
    { title: 'Phòng khách Ánh Nắng & Linen', description: 'Một khoảng dừng dịu dàng giữa nhịp sống.', image: image('phong-khach-nordic', 'Không gian phòng khách với sofa và bàn trà'), href: '/san-pham' },
    { title: 'Phòng ăn Gỗ Sồi Japandi', description: 'Nơi những câu chuyện được sẻ chia.', image: image('phong-an-japandi', 'Không gian phòng ăn với bàn và ghế sáng màu'), href: '/san-pham?category=ban-ghe' },
  ],
};
