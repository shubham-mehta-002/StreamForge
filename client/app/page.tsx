import { VideoGallery } from "@/components/video/VideoGallery";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Upload } from "lucide-react";

export default function Home() {
  return (
    <div className="container mx-auto px-4 py-12">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-12 gap-6">
        <div>
          <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight mb-3">
            Discover Videos
          </h1>
          <p className="text-muted-foreground text-lg max-w-2xl">
            Watch the latest streams uploaded by the community, powered by high-performance HLS.
          </p>
        </div>
        
        <Link href="/upload">
          <Button size="lg" className="rounded-full font-semibold px-8 shadow-md hover:shadow-xl transition-all">
            <Upload className="w-5 h-5 mr-2" /> Upload Video
          </Button>
        </Link>
      </div>
      
      <div className="relative">
        <div className="absolute inset-0 bg-gradient-to-r from-primary/5 to-secondary/5 rounded-3xl -z-10" />
        <div className="p-2 md:p-6">
          <VideoGallery />
        </div>
      </div>
    </div>
  );
}
