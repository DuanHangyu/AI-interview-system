import * as echarts from "echarts";
import { ref } from "vue";

export default (id: string) => {
  let myChart: echarts.ECharts | null = null;

  const tmpOption = ref();
  const setOption = (list: Record<string, any>[]) => {
    if (!myChart) {
      const dom = document.querySelector(`#${id}`) as HTMLElement;
      myChart = echarts.init(dom);
    }
    tmpOption.value = {
      tooltip: {
        show: true,
        trigger: 'item',
        extraCssText: 'width:200px;height:auto;white-space: wrap;'
      },
      legend: {
        top: "bottom",
        textStyle: {
          color: "#666",
          fontSize: "0.8rem",
        },
        itemWidth: 30,
        itemHeight: 14,
        itemGap: 18,
        // formatter: function (name: string) {
        //   return name.length > 8 ? name.substring(0, 8) + '...' : name;
        // },
        // tooltip: {
        //   show: true
        // },
        formatter: function (name: string) {
          return echarts.format.truncateText(name, 130, '14px Microsoft Yahei', '…');
        },
        tooltip: {
          show: true,
        },
        // data: [
        //   {
        //     name: "数学原理",
        //     icon: "image://data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADwAAAAcCAYAAAA9UNxEAAACGElEQVRYR+VZPVLCUBB++4LjaOURYoU2mpxAPIHQOgjYWXIDuIGWdog/YwmeQDxBMjQiOkNuIJ2F5q27GYOoKElmnMkLabP7st983+7b3YCI8NTunQIaxp5AVRAgTKXEWgS3fzeRUowpFldK6YLv35xt2r15H4W/DAhoDQEaSghz3kFpeC+F8CRAs5Xfbv8Wz0zAR/eO+SKho1BYaQASNwYGvoK4e7ppe999fwAOWDXgOC2yjQs2tCdgYwV4eJm3u9NnfAFcfnCKgNBJ+pE0+uUAatMSnwAOZGyAozuzMyQ8XkW0Q3lPAFcH7kiX4hRXSRKE285bNvsFgDlvfYBW3IN0sg+lHQDOMrshKVy52xvWOnBTQeze6sRW0lgNuqqg+tg/Vr6qJz1EJz/qyE7gYOAyuwWdAk8aK1LxgsrQfcaU9MZJgUT142aEGcaoDlmwWzzACyfp6tDldlLLqShuilHu9hbvWlqkxkMsy0LQWlZocCC6zbgS0cmegHrn3Fpy0Ps0PBgZHx4UzcVXtPr5HA8zXLxCdifj4ceIaCpaAGSt6wLabEofbdpoel8AZ3UufgMsXU/ttX4s8cpPTlEqaOnONDPrI9Q5b6eL68w1LV1VJiX5ra6Vm6einMJSKOO5gEMDrt60GmnoApyLEy0zmt9ZjQw4NDwY9Xfkqyj6Sln0e8NKi9xZtrR49Awhe2pJdC/Wt+7m9QbvarzbbQJiC3UAAAAASUVORK5CYII=",
        //   },
        //   {
        //     name: "知识运用",
        //     icon: "image://data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADwAAAAcCAYAAAA9UNxEAAABtklEQVRYR+VY0VHCQBB9m1O/LQE7gArECsQOkBFndJwBKtAOQvjwA1DGCsQKwApIB9KBfKvJukkAIxEhMs5wufvN7WXfvXd3u4+wxuCOXQTUMcBFEHJg7K8R9v9TCBOAXMnHBbwnqjaGq35Kv03gtl2Gsq7hC0gdBmOMHXVDlauHZen+CJh7dg4f1qME5XXAmcgxAO75R3TRGC9+SwDmdqsMi+2tke1fdzyUu39KZ41+fIlvgLlrl8Ahs9kZSpXjEp8DDmXsWSPtmU1qeIJ3vzCT9xfgO+dFm8spvf5cqtYKQVgIOLyNyeqlX0ejiKm0I8DZZjdiRW5uOq8dUFRUWAONuNogVXmquNO0Rdn1DVbRKJSbxN3WACwloxnDFcDOa+aeomXkSTEiknbYDHIjlAYCNlDSI2Faz64o7VkkHhr4LJlUeKjdonmlpZHNQwi642T38po2DvP2MARsmgGQWWmTfxL3tZImXuBrQcyAbfGe0761s/mBiWep+qJlu9ym9aVH1sWPTm6Kiw9hdh2bNh5rjBG/uGF8f3sI760kDpiUoJTfGrkHsmUeS75DqL0+VS6fV52AT4a5vdgO7t88AAAAAElFTkSuQmCC",
        //   },
        //   {
        //     name: "问题分析",
        //     icon: "image://data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADwAAAAcCAYAAAA9UNxEAAACFklEQVRYR+VYS1LCQBDt7gQov6VLV8AN4ATiCdQb4FJKLT2B3gALKFxankA9AXgCuUFAFy5NadBSyLQzaCjkK1lYmWRWqcr0pF/3S0/3Q/jDei1bOQONbQbOMUNKmqz9wew/ttiA1CDghsvu7cphuj7rozhtg1Oy8kR0Kr5BBn4RQtMkOIsXkleTnB0L+P3CSglB1ywgE3iUYxxUwNEQWwv76ebw6xHAzoWVhy4VA0RbvzG3hSH2Vgvpm8EDfgFuV60dduna7xeCaBc3ID9I8T5gRWO3S/chyOxw3G3DFFmP3n3Ab5WWpUtxmpdJiNBYOkhmlV0PsKrGsrxfznuQTvs9avcAhzm7XlJU5V48SKZRNRUIVNMpW359ZRBb6JQeioB87PcQrewYz9GpPNaARU4rx306i8gNdMqt5xBeRZNCYivA7DNgWppFEnC0KN2utO7ljKvlVDT3P4Vcj961FKXGI2HEZZMlV7vUshj1UDXmpvGPATI0l45kaxmV4QHkXLwspZ/+eBjm4uVltz8eqofnopWKmeETAOQNZHddkV0/+da3fkk8YZyLpa61O6hrjYh4L1LXIrcnBgRFe/ZVp1RmEyYcD0u2Y2VaRe+4QTVdK7eSdD47Ytej8WDEZgrxUhw41QW4Kk5swpmqxpNoMRWwZ9SpPm1+dDs7QCLDAjMygoGgu6ItETdZUD1hxm5ihY27Wfz/AhRk10Ixkv4QAAAAAElFTkSuQmCC",
        //   },
        //   {
        //     name: "工程能力",
        //     icon: "image://data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADwAAAAcCAYAAAA9UNxEAAAB80lEQVRYR+VZS07CUBQ9t4AjTVwC7gBXIA4ViTBziDtg5MBPIIqQOMEdEGfOxBh0CK4AdmB3IIlxovRd76sW+QmUxITXvlGT3vd6T+/p/ZwS5liZC046EewrRtJixBWwPse2fzexgC4DHbLQiTi4r59Ra9ZDaZrBboVzxChAIT7roCW5b4sfxcYp3fzlz0TABxWOvyncgZFYEiB+3bDXeti+LZJ+AUNrDHD6nHOwUF0W2vpF6tkLsK5j4fDpmOqDZwwB3ilzxtKRDdbKDVK8D1jT+N1B2/TITqBwd7WHTY/efcCpMr8YlJz8cZDQaZzQpt7kAnazsYOav1OMs3ap7QIOdHR/42IL4A3STcUnoWlcvBZwOMbYpnSZq0ohv8B+47YoC9e0V+KmtGdJ47xfxGFJXpQu8WvQStGUtrJLqRJLgMOzwgc4fJS+5LbBU5Gvb1Gajlb4ylKYGg9rBcnvXloGBzJH1fBFY89YSpH9qFvLUA4P7gAR4OTlRbc/HuqLjAgATkAFgKgIAPUffWtI4gniXCwDQ3ZQ1xoX8UTXEuWjZnp/rUU8oXJ+VLKdKNNqen8wmsZmbpmKYp/IejQeTOszhXjpwgqmANfJSQD5F+JHC136ird6PWQiyhXmE3LwUvxqcWlLsBWhFY2i/nBEz7OK9BdM1MhA0Vj94gAAAABJRU5ErkJggg==",
        //   },
        //   {
        //     name: "拓展能力",
        //     icon: "image://data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADwAAAAcCAYAAAA9UNxEAAAB/UlEQVRYR+VZTU7CUBCeaRuJiQu4AdwATiCcQLgB7lUqYsJOly5AwJ+18QTgCcATwA3kBm3igkjaN86rgAUk0CYmfbSrJn0zna/zvXkzXxF2uGo1Ky8c44TIzSNAGgiSO5j9/xIEmxBGCPpIM5y3RiM12PZSjn/zVTWtMhDeMMD0NkeReI4wRjJumw9Hr5vi+RNwvW6lvybYRYJsJIAEDYKBHyAV7tqp8arpGuBZVluRoW1QsPP1THd06bT5lOr5XSwBvjqziqRhN+w7omiHrlFuPv9SfAFY0ng6waHymV3nsM30zs3pvQBcrdgfyhSngFSSlbzVSeakmQfY27cCXwL6UWo5AlObq/cP4D3O7iIrXLnvO8kMek3FFPtKpStksJpGBayany0QrhnSh1pmmt7Gmmn3hYC8WpGHi9ZrQ3n/Wnt3FG3uK22sXtgU7nupaRVDwHGjdK1iD4WqU1HAXUUEgzgeS9x4iHg0HkQJqdgAXHJrycN+OiBDlFrOZ/CYB4hMPIeHWZaHyko6W7g2z+5iPJQ3sRMA9nUuRkElv661JuJ5upbOYkBUtOewpVGKeI5h+vWsJUr7/c5k2r6qlVtORQmk0k4yrR+4lH6IhXhVgMvipIUR4ldZdH0+OXZ1p6i5blZoLM5Hhe7yVwvw3wbUB+Qavdbj4fu2HfANMArcVmlQR5gAAAAASUVORK5CYII=",
        //   },
        // ],
        data: list,
      },
      grid: {
        containLabel: true,
        top: "5%",
        right: "5%",
        bottom: "6%",
        left: "5%",
      },
      series: [
        {
          name: "",
          type: "pie",
          radius: ["20%", "60%"],
          center: ["50%", "40%"],
          roseType: "area",
          itemStyle: {
            borderRadius: 5,
          },
          label: {
            color: "#666",
            fontSize: "1rem",
            // formatter: "{c}分",
            formatter: (parmas: Recordable) => {
              return parmas?.data?.originValule + "分";
            },
          },
          data: list?.map((item) => {
            item.originValule = item.value;
            item.value = item?.value + 0.1;
            return item;
          }),
        },
      ],
    };

    myChart.setOption(tmpOption.value);
    window.onresize = () => onresize();

    const onresize = () => {
      myChart?.resize();
      const width = document.querySelector(`#${id}`)?.clientWidth;
      if (width) {
        myChart?.setOption(tmpOption.value);
      }
    };
    onresize();
  };

  return { setOption };
};
